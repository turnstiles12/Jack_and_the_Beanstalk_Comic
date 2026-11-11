package com.beanstalk;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.LongSupplier;

/**
 * Stateless signed session cookie: base64url(json) + "." + base64url(HMAC-SHA256).
 * Nothing is stored server-side, so sessions survive restarts and redeploys.
 */
public final class AuthTokens {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder B64D = Base64.getUrlDecoder();

    private final byte[] key;
    private final LongSupplier nowSeconds;

    public AuthTokens(byte[] key) { this(key, () -> System.currentTimeMillis() / 1000); }

    AuthTokens(byte[] key, LongSupplier nowSeconds) {
        this.key = key.clone();
        this.nowSeconds = nowSeconds;
    }

    public String issue(AuthSession s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("pw", s.passwordOk());
        m.put("sub", s.sub());
        m.put("em", s.email());
        m.put("nm", s.name());
        m.put("pic", s.picture());
        m.put("exp", s.exp());
        try {
            String payload = B64.encodeToString(JSON.writeValueAsBytes(m));
            return payload + "." + B64.encodeToString(hmac(payload));
        } catch (Exception e) {
            throw new IllegalStateException("Could not sign session", e);
        }
    }

    /** Empty if the token is missing, malformed, tampered with, or expired. */
    public Optional<AuthSession> parse(String token) {
        try {
            if (token == null) return Optional.empty();
            int dot = token.indexOf('.');
            if (dot <= 0 || dot == token.length() - 1) return Optional.empty();
            String payload = token.substring(0, dot);
            byte[] given = B64D.decode(token.substring(dot + 1));
            if (!MessageDigest.isEqual(hmac(payload), given)) return Optional.empty();   // constant-time compare
            Map<String, Object> m = JSON.readValue(B64D.decode(payload), new TypeReference<Map<String, Object>>() {});
            long exp = ((Number) m.get("exp")).longValue();
            if (exp <= nowSeconds.getAsLong()) return Optional.empty();
            return Optional.of(new AuthSession(Boolean.TRUE.equals(m.get("pw")),
                    str(m.get("sub")), str(m.get("em")), str(m.get("nm")), str(m.get("pic")), exp));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private static String str(Object o) { return o == null ? null : o.toString(); }

    private byte[] hmac(String payload) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(payload.getBytes(StandardCharsets.US_ASCII));
    }
}
