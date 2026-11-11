package com.beanstalk;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Two-step access control: (1) the shared site password, (2) a Google account.
 * Both results live in one HMAC-signed, HttpOnly cookie (see {@link AuthTokens}).
 * The signing key mixes SESSION_SECRET with the site password, so changing the password
 * instantly logs everybody out.
 */
@Service
public class AuthService {

    public static final String COOKIE = "bsc";
    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final int MAX_FAILURES = 5;
    private static final long WINDOW_MS = 10 * 60_000L;

    private final byte[] passwordDigest;
    private final boolean passwordConfigured;
    private final String googleClientId;
    private final boolean cookieSecure;
    private final long maxAgeSeconds;
    private final AuthTokens tokens;
    private final GoogleIdTokenVerifier google;
    private final Map<String, Deque<Long>> failures = new ConcurrentHashMap<>();

    public AuthService(@Value("${beanstalk.auth.site-password:}") String sitePassword,
                       @Value("${beanstalk.auth.session-secret:}") String sessionSecret,
                       @Value("${beanstalk.auth.google-client-id:}") String googleClientId,
                       @Value("${beanstalk.auth.cookie-secure:false}") boolean cookieSecure,
                       @Value("${beanstalk.auth.session-days:30}") int sessionDays) {
        this.passwordConfigured = sitePassword != null && !sitePassword.isEmpty();
        if (!passwordConfigured)
            log.warn("SITE_PASSWORD is not set: the password gate will reject everyone until it is.");
        String pw = passwordConfigured ? sitePassword : "";
        this.passwordDigest = sha256(pw);

        if (sessionSecret == null || sessionSecret.isBlank()) {
            byte[] rnd = new byte[32];
            new SecureRandom().nextBytes(rnd);
            sessionSecret = java.util.HexFormat.of().formatHex(rnd);
            log.warn("SESSION_SECRET is not set: using a random one, so everyone is signed out on every restart.");
        }
        this.tokens = new AuthTokens(sha256(sessionSecret + "\0" + pw));
        this.googleClientId = googleClientId == null ? "" : googleClientId.strip();
        this.google = new GoogleIdTokenVerifier(this.googleClientId);
        if (this.googleClientId.isEmpty())
            log.warn("GOOGLE_CLIENT_ID is not set: Google sign-in will be unavailable.");
        this.cookieSecure = cookieSecure;
        this.maxAgeSeconds = Duration.ofDays(Math.max(1, sessionDays)).toSeconds();
    }

    public boolean passwordConfigured() { return passwordConfigured; }

    public boolean googleConfigured() { return !googleClientId.isEmpty(); }

    public String googleClientId() { return googleClientId; }

    /** Constant-time comparison (of digests, so length doesn't leak either). */
    public boolean checkPassword(String attempt) {
        if (!passwordConfigured || attempt == null) return false;
        return MessageDigest.isEqual(passwordDigest, sha256(attempt));
    }

    public GoogleIdTokenVerifier.GoogleUser verifyGoogle(String credential)
            throws GoogleIdTokenVerifier.InvalidTokenException {
        return google.verify(credential);
    }

    // ---- session cookie ----

    public AuthSession read(HttpServletRequest req) {
        Cookie[] cs = req.getCookies();
        if (cs == null) return AuthSession.anonymous();
        for (Cookie c : cs)
            if (COOKIE.equals(c.getName())) return tokens.parse(c.getValue()).orElse(AuthSession.anonymous());
        return AuthSession.anonymous();
    }

    public void write(HttpServletResponse res, boolean passwordOk, GoogleIdTokenVerifier.GoogleUser user) {
        long exp = System.currentTimeMillis() / 1000 + maxAgeSeconds;
        AuthSession s = user == null
                ? new AuthSession(passwordOk, null, null, null, null, exp)
                : new AuthSession(passwordOk, user.sub(), user.email(), user.name(), user.picture(), exp);
        ResponseCookie cookie = ResponseCookie.from(COOKIE, tokens.issue(s))
                .httpOnly(true).secure(cookieSecure).sameSite("Lax").path("/")
                .maxAge(Duration.ofSeconds(maxAgeSeconds)).build();
        res.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    // ---- brute-force throttle for the password gate (per client address) ----

    public boolean locked(String key) {
        Deque<Long> d = failures.get(key);
        if (d == null) return false;
        synchronized (d) { prune(d); return d.size() >= MAX_FAILURES; }
    }

    public void recordFailure(String key) {
        if (failures.size() > 10_000) failures.clear();
        Deque<Long> d = failures.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (d) { prune(d); d.addLast(System.currentTimeMillis()); }
    }

    public void clearFailures(String key) { failures.remove(key); }

    private static void prune(Deque<Long> d) {
        long cutoff = System.currentTimeMillis() - WINDOW_MS;
        while (!d.isEmpty() && d.peekFirst() < cutoff) d.removeFirst();
    }

    /** Behind Render's proxy the real client is the first X-Forwarded-For entry. */
    public static String clientKey(HttpServletRequest req) {
        String xff = req.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) return xff.split(",")[0].strip();
        return req.getRemoteAddr();
    }

    private static byte[] sha256(String s) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
