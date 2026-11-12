package com.beanstalk;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AccessControlTest {

    private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String CLIENT = "my-client.apps.googleusercontent.com";

    // ---------- signed session cookie ----------

    @Test
    void sessionCookieRoundTripsAndRejectsTampering() {
        long[] now = {1_000};
        AuthTokens t = new AuthTokens("k1".getBytes(StandardCharsets.UTF_8), () -> now[0]);
        String tok = t.issue(new AuthSession(true, "sub-1", "a@b.com", "Ann", "http://pic", 2_000));
        AuthSession s = t.parse(tok).orElseThrow();
        assertTrue(s.passwordOk());
        assertEquals("sub-1", s.sub());
        assertEquals("a@b.com", s.email());
        assertTrue(s.signedIn());

        // flip the user id inside the payload but keep the old signature
        String[] parts = tok.split("\\.");
        String forged = new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8).replace("sub-1", "sub-2");
        assertTrue(t.parse(B64.encodeToString(forged.getBytes(StandardCharsets.UTF_8)) + "." + parts[1]).isEmpty());

        // a different key (e.g. the site password changed) invalidates old cookies
        assertTrue(new AuthTokens("k2".getBytes(StandardCharsets.UTF_8), () -> now[0]).parse(tok).isEmpty());

        now[0] = 3_000;   // expired
        assertTrue(t.parse(tok).isEmpty());
        assertTrue(t.parse(null).isEmpty());
        assertTrue(t.parse("garbage").isEmpty());
        assertTrue(t.parse(".").isEmpty());
    }

    @Test
    void passwordOnlySessionIsNotSignedIn() {
        AuthTokens t = new AuthTokens("k".getBytes(StandardCharsets.UTF_8), () -> 0);
        AuthSession s = t.parse(t.issue(new AuthSession(true, null, null, null, null, 10))).orElseThrow();
        assertTrue(s.passwordOk());
        assertFalse(s.signedIn());
        assertFalse(AuthSession.anonymous().passwordOk());
    }

    @Test
    void filterNormalisationCannotHideApiPaths() {
        assertEquals("/api/books", ApiPaths.normalise("//api//books"));
        assertEquals("/api/books", ApiPaths.normalise("/api;x=1/books"));
        assertEquals("/api/books/abc", ApiPaths.normalise("/api/books;jsessionid=1/abc"));
    }

    // ---------- Google ID token ----------

    private record Fixture(KeyPair kp, GoogleIdTokenVerifier verifier) {}

    private static Fixture fixture(long now) throws Exception {
        KeyPairGenerator g = KeyPairGenerator.getInstance("RSA");
        g.initialize(2048);
        KeyPair kp = g.generateKeyPair();
        RSAPublicKey pub = (RSAPublicKey) kp.getPublic();
        String jwks = JSON.writeValueAsString(Map.of("keys", List.of(Map.of(
                "kty", "RSA", "kid", "k1", "alg", "RS256",
                "n", B64.encodeToString(unsigned(pub.getModulus().toByteArray())),
                "e", B64.encodeToString(unsigned(pub.getPublicExponent().toByteArray()))))));
        return new Fixture(kp, new GoogleIdTokenVerifier(CLIENT, () -> jwks, () -> now));
    }

    private static byte[] unsigned(byte[] b) {
        return b.length > 1 && b[0] == 0 ? java.util.Arrays.copyOfRange(b, 1, b.length) : b;
    }

    private static String jwt(KeyPair kp, String alg, Map<String, Object> claims) throws Exception {
        String h = B64.encodeToString(JSON.writeValueAsBytes(Map.of("alg", alg, "kid", "k1", "typ", "JWT")));
        String p = B64.encodeToString(JSON.writeValueAsBytes(claims));
        Signature s = Signature.getInstance("SHA256withRSA");
        s.initSign(kp.getPrivate());
        s.update((h + "." + p).getBytes(StandardCharsets.US_ASCII));
        return h + "." + p + "." + B64.encodeToString(s.sign());
    }

    private static Map<String, Object> goodClaims() {
        return new java.util.HashMap<>(Map.of("iss", "https://accounts.google.com", "aud", CLIENT, "exp", 5_000,
                "sub", "1234567890", "email", "kid@example.com", "email_verified", true, "name", "Kid Example"));
    }

    @Test
    void acceptsAValidGoogleToken() throws Exception {
        Fixture f = fixture(4_000);
        GoogleIdTokenVerifier.GoogleUser u = f.verifier().verify(jwt(f.kp(), "RS256", goodClaims()));
        assertEquals("1234567890", u.sub());
        assertEquals("kid@example.com", u.email());
        assertEquals("Kid Example", u.name());
    }

    @Test
    void rejectsBadGoogleTokens() throws Exception {
        Fixture f = fixture(4_000);
        GoogleIdTokenVerifier v = f.verifier();

        Map<String, Object> wrongAud = goodClaims(); wrongAud.put("aud", "someone-elses-app");
        assertThrows(GoogleIdTokenVerifier.InvalidTokenException.class, () -> v.verify(jwt(f.kp(), "RS256", wrongAud)));

        Map<String, Object> wrongIss = goodClaims(); wrongIss.put("iss", "https://evil.example");
        assertThrows(GoogleIdTokenVerifier.InvalidTokenException.class, () -> v.verify(jwt(f.kp(), "RS256", wrongIss)));

        Map<String, Object> expired = goodClaims(); expired.put("exp", 100);
        assertThrows(GoogleIdTokenVerifier.InvalidTokenException.class, () -> v.verify(jwt(f.kp(), "RS256", expired)));

        Map<String, Object> unverified = goodClaims(); unverified.put("email_verified", false);
        assertThrows(GoogleIdTokenVerifier.InvalidTokenException.class, () -> v.verify(jwt(f.kp(), "RS256", unverified)));

        // alg=none style downgrade
        assertThrows(GoogleIdTokenVerifier.InvalidTokenException.class, () -> v.verify(jwt(f.kp(), "none", goodClaims())));

        // signed by a different key than Google's
        KeyPairGenerator g = KeyPairGenerator.getInstance("RSA"); g.initialize(2048);
        KeyPair attacker = g.generateKeyPair();
        assertThrows(GoogleIdTokenVerifier.InvalidTokenException.class, () -> v.verify(jwt(attacker, "RS256", goodClaims())));

        // payload swapped after signing
        String[] parts = jwt(f.kp(), "RS256", goodClaims()).split("\\.");
        Map<String, Object> other = goodClaims(); other.put("sub", "999");
        String swapped = parts[0] + "." + B64.encodeToString(JSON.writeValueAsBytes(other)) + "." + parts[2];
        assertThrows(GoogleIdTokenVerifier.InvalidTokenException.class, () -> v.verify(swapped));

        assertThrows(GoogleIdTokenVerifier.InvalidTokenException.class, () -> v.verify("not-a-jwt"));
        assertThrows(GoogleIdTokenVerifier.InvalidTokenException.class, () -> v.verify(null));
    }

    // ---------- per-account storage ----------

    /** Needs a real Postgres: set TEST_DATABASE_URL to a throwaway database (e.g. a Neon branch). Skipped otherwise. */
    @Test
    void booksAreSavedPerAccountAndNeverLeakAcrossAccounts() throws Exception {
        String url = System.getenv("TEST_DATABASE_URL");
        org.junit.jupiter.api.Assumptions.assumeTrue(url != null && !url.isBlank(), "TEST_DATABASE_URL not set");
        javax.sql.DataSource ds = Db.build(url);
        try (var c = ds.getConnection(); var st = c.createStatement()) { for (String ddl : Db.SCHEMA) st.execute(ddl); }
        BookStore store = new BookStore(ds);
        String id = java.util.UUID.randomUUID().toString();
        byte[] pdf = "%PDF-fake".getBytes(StandardCharsets.UTF_8);
        store.save("google-sub-A", id, "Mason", pdf, new byte[]{1, 2, 3});

        // "signing back in" = a brand new BookStore instance on the same database
        BookStore later = new BookStore(ds);
        List<BookStore.Meta> mine = later.list("google-sub-A");
        assertEquals(1, mine.size());
        assertEquals("Mason", mine.get(0).name());
        assertArrayEquals(pdf, later.pdf("google-sub-A", id).orElseThrow());
        assertArrayEquals(new byte[]{1, 2, 3}, later.thumb("google-sub-A", id).orElseThrow());

        assertTrue(later.list("google-sub-B").isEmpty());
        assertTrue(later.pdf("google-sub-B", id).isEmpty());
        assertTrue(later.meta("google-sub-B", id).isEmpty());
        assertFalse(later.delete("google-sub-B", id));

        // path-traversal style ids are refused
        assertTrue(later.pdf("google-sub-A", "../../etc/passwd").isEmpty());
        assertThrows(IllegalArgumentException.class, () -> later.save("google-sub-A", "../x", "n", pdf, null));

        assertTrue(later.delete("google-sub-A", id));
        assertTrue(later.list("google-sub-A").isEmpty());
    }
}
