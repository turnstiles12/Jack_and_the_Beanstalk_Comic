package com.beanstalk;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.math.BigInteger;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.RSAPublicKeySpec;
import java.time.Duration;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * Verifies a "Sign in with Google" ID token (a JWT) locally: RS256 signature against Google's published
 * keys, then issuer, audience (must be OUR client id), expiry and verified e-mail. JDK + Jackson only.
 */
public final class GoogleIdTokenVerifier {

    public record GoogleUser(String sub, String email, String name, String picture) {}

    public static final class InvalidTokenException extends Exception {
        public InvalidTokenException(String msg) { super(msg); }
    }

    private static final String CERTS_URL = "https://www.googleapis.com/oauth2/v3/certs";
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Base64.Decoder B64D = Base64.getUrlDecoder();
    private static final long CLOCK_SKEW_SECONDS = 60;

    private final String clientId;
    private final Supplier<String> jwksFetcher;
    private final LongSupplier nowSeconds;
    private volatile Map<String, PublicKey> keys = Map.of();
    private volatile long keysFetchedAtMs = 0;

    public GoogleIdTokenVerifier(String clientId) {
        this(clientId, GoogleIdTokenVerifier::fetchCerts, () -> System.currentTimeMillis() / 1000);
    }

    GoogleIdTokenVerifier(String clientId, Supplier<String> jwksFetcher, LongSupplier nowSeconds) {
        this.clientId = clientId;
        this.jwksFetcher = jwksFetcher;
        this.nowSeconds = nowSeconds;
    }

    public GoogleUser verify(String idToken) throws InvalidTokenException {
        if (clientId == null || clientId.isBlank()) throw new InvalidTokenException("Google sign-in is not configured.");
        if (idToken == null || idToken.length() > 4096) throw new InvalidTokenException("Missing token.");
        String[] parts = idToken.split("\\.", -1);
        if (parts.length != 3) throw new InvalidTokenException("Malformed token.");
        try {
            JsonNode header = JSON.readTree(B64D.decode(parts[0]));
            if (!"RS256".equals(header.path("alg").asText())) throw new InvalidTokenException("Unsupported token algorithm.");
            PublicKey key = keyFor(header.path("kid").asText(""));
            if (key == null) throw new InvalidTokenException("Unknown signing key.");

            Signature sig = Signature.getInstance("SHA256withRSA");
            sig.initVerify(key);
            sig.update((parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII));
            if (!sig.verify(B64D.decode(parts[2]))) throw new InvalidTokenException("Bad signature.");

            JsonNode c = JSON.readTree(B64D.decode(parts[1]));
            String iss = c.path("iss").asText("");
            if (!iss.equals("https://accounts.google.com") && !iss.equals("accounts.google.com"))
                throw new InvalidTokenException("Wrong issuer.");
            if (!audienceMatches(c.path("aud"))) throw new InvalidTokenException("Token was issued for a different app.");
            if (c.path("exp").asLong(0) + CLOCK_SKEW_SECONDS < nowSeconds.getAsLong())
                throw new InvalidTokenException("Token expired. Please try again.");
            String sub = c.path("sub").asText("");
            String email = c.path("email").asText("");
            if (sub.isBlank() || email.isBlank()) throw new InvalidTokenException("Token has no account info.");
            if (!c.path("email_verified").asBoolean(false)) throw new InvalidTokenException("Google e-mail is not verified.");
            return new GoogleUser(sub, email, c.path("name").asText(email), c.path("picture").asText(""));
        } catch (InvalidTokenException e) {
            throw e;
        } catch (Exception e) {
            throw new InvalidTokenException("Could not verify the token.");
        }
    }

    private boolean audienceMatches(JsonNode aud) {
        if (aud.isArray()) {
            for (JsonNode n : aud) if (clientId.equals(n.asText())) return true;
            return false;
        }
        return clientId.equals(aud.asText());
    }

    /** Cached for an hour; an unknown key id triggers at most one refetch per minute (key rotation). */
    private PublicKey keyFor(String kid) throws InvalidTokenException {
        long nowMs = System.currentTimeMillis();
        PublicKey k = keys.get(kid);
        boolean stale = nowMs - keysFetchedAtMs > 3_600_000L;
        boolean mayRetry = nowMs - keysFetchedAtMs > 60_000L;
        if ((k == null && mayRetry) || stale) {
            refresh();
            k = keys.get(kid);
        }
        return k;
    }

    private synchronized void refresh() throws InvalidTokenException {
        if (System.currentTimeMillis() - keysFetchedAtMs < 5_000L) return;   // another thread just did it
        keysFetchedAtMs = System.currentTimeMillis();
        try {
            JsonNode root = JSON.readTree(jwksFetcher.get());
            Map<String, PublicKey> fresh = new HashMap<>();
            KeyFactory kf = KeyFactory.getInstance("RSA");
            for (JsonNode k : root.path("keys")) {
                if (!"RSA".equals(k.path("kty").asText())) continue;
                BigInteger n = new BigInteger(1, B64D.decode(k.path("n").asText()));
                BigInteger e = new BigInteger(1, B64D.decode(k.path("e").asText()));
                fresh.put(k.path("kid").asText(), kf.generatePublic(new RSAPublicKeySpec(n, e)));
            }
            if (!fresh.isEmpty()) keys = fresh;
        } catch (Exception e) {
            if (keys.isEmpty()) throw new InvalidTokenException("Could not reach Google to verify sign-in. Try again shortly.");
        }
    }

    private static String fetchCerts() {
        try {
            HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
            HttpResponse<String> r = http.send(HttpRequest.newBuilder(URI.create(CERTS_URL))
                    .timeout(Duration.ofSeconds(5)).GET().build(), HttpResponse.BodyHandlers.ofString());
            if (r.statusCode() != 200) throw new IllegalStateException("HTTP " + r.statusCode());
            return r.body();
        } catch (Exception e) {
            throw new IllegalStateException("Could not fetch Google keys", e);
        }
    }
}
