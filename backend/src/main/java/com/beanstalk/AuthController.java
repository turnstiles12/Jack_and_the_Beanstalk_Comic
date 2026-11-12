package com.beanstalk;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService auth;

    private final UserStore users;

    public AuthController(AuthService auth, UserStore users) { this.auth = auth; this.users = users; }

    /** What the UI should show next: the password gate, the Google sign-in page, or the app. */
    @GetMapping("/status")
    public Map<String, Object> status(HttpServletRequest req) {
        AuthSession s = auth.read(req);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("passwordOk", s.passwordOk());
        m.put("googleClientId", s.passwordOk() ? auth.googleClientId() : "");
        m.put("user", s.signedIn() ? userJson(s.email(), s.name(), s.picture()) : null);
        return m;
    }

    @PostMapping("/password")
    public ResponseEntity<Map<String, Object>> password(@RequestBody Map<String, String> body,
                                                        HttpServletRequest req, HttpServletResponse res) {
        if (!auth.passwordConfigured())
            return error(503, "The site password has not been set up on the server yet.", null);
        String key = AuthService.clientKey(req);
        if (auth.locked(key))
            return error(429, "Too many wrong attempts. Please wait a few minutes and try again.", null);
        if (!auth.checkPassword(body.get("password"))) {
            auth.recordFailure(key);
            return error(401, "Incorrect password.", null);
        }
        auth.clearFailures(key);
        AuthSession cur = auth.read(req);
        auth.write(res, true, cur.signedIn()
                ? new GoogleIdTokenVerifier.GoogleUser(cur.sub(), cur.email(), cur.name(), cur.picture()) : null);
        return ResponseEntity.ok(Map.<String, Object>of("ok", true));
    }

    @PostMapping("/google")
    public ResponseEntity<Map<String, Object>> google(@RequestBody Map<String, String> body,
                                                      HttpServletRequest req, HttpServletResponse res) {
        if (!auth.read(req).passwordOk())
            return error(401, "Enter the site password first.", "PASSWORD_REQUIRED");
        if (!auth.googleConfigured())
            return error(503, "Google sign-in has not been set up on the server yet.", null);
        try {
            GoogleIdTokenVerifier.GoogleUser u = auth.verifyGoogle(body.get("credential"));
            users.upsert(u.sub(), u.email(), u.name(), u.picture());
            auth.write(res, true, u);
            return ResponseEntity.ok(userJson(u.email(), u.name(), u.picture()));
        } catch (GoogleIdTokenVerifier.InvalidTokenException e) {
            return error(401, "Google sign-in failed: " + e.getMessage(), null);
        }
    }

    /** Signs the Google account out but keeps the password step, so the next person lands on the Google page. */
    @PostMapping("/logout")
    public Map<String, Object> logout(HttpServletRequest req, HttpServletResponse res) {
        auth.write(res, auth.read(req).passwordOk(), null);
        return Map.of("ok", true);
    }

    private static Map<String, Object> userJson(String email, String name, String picture) {
        Map<String, Object> u = new LinkedHashMap<>();
        u.put("email", email == null ? "" : email);
        u.put("name", name == null ? "" : name);
        u.put("picture", picture == null ? "" : picture);
        return u;
    }

    private static ResponseEntity<Map<String, Object>> error(int status, String msg, String code) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("error", msg);
        if (code != null) m.put("code", code);
        return ResponseEntity.status(status).body(m);
    }
}
