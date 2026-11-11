package com.beanstalk;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Every /api/** call except /api/health and /api/auth/** needs BOTH the password step and a Google
 * sign-in. The signed-in user is handed to controllers as a request attribute.
 */
@Component
public class AuthFilter extends OncePerRequestFilter {

    public static final String USER = "bs.user";

    private final AuthService auth;

    public AuthFilter(AuthService auth) { this.auth = auth; }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest req) {
        String p = ApiPaths.normalise(req.getRequestURI());
        boolean api = p.equals("/api") || p.startsWith("/api/");
        boolean open = p.equals("/api/health") || p.startsWith("/api/auth/");
        return !api || open;          // static front-end files hold no secrets; all data is behind /api
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        AuthSession s = auth.read(req);
        if (!s.passwordOk()) { deny(res, "PASSWORD_REQUIRED", "Enter the site password first."); return; }
        if (!s.signedIn())   { deny(res, "LOGIN_REQUIRED", "Please sign in with Google."); return; }
        req.setAttribute(USER, s);
        chain.doFilter(req, res);
    }

    private static void deny(HttpServletResponse res, String code, String msg) throws IOException {
        res.setStatus(401);
        res.setContentType("application/json");
        res.setCharacterEncoding("UTF-8");
        res.getOutputStream().write(("{\"error\":\"" + msg + "\",\"code\":\"" + code + "\"}").getBytes(StandardCharsets.UTF_8));
    }
}
