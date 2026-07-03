package com.scanlanka.auth.web;

import com.scanlanka.auth.app.AuthService;
import com.scanlanka.auth.web.dto.AuthRequests.ForgotRequest;
import com.scanlanka.auth.web.dto.AuthRequests.LoginRequest;
import com.scanlanka.auth.web.dto.AuthRequests.RegisterRequest;
import com.scanlanka.auth.web.dto.AuthRequests.ResetRequest;
import com.scanlanka.auth.web.dto.AuthRequests.VerifyEmailRequest;
import com.scanlanka.shared.ratelimit.RateLimiter;
import com.scanlanka.shared.security.AuthPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

/** Auth endpoints (07-auth-accounts §3). Thin controller: rate-limit → service → set cookies. */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService auth;
    private final CookieFactory cookies;
    private final RateLimiter rateLimiter;

    public AuthController(AuthService auth, CookieFactory cookies, RateLimiter rateLimiter) {
        this.auth = auth;
        this.cookies = cookies;
        this.rateLimiter = rateLimiter;
    }

    @PostMapping("/register")
    public ResponseEntity<Void> register(@Valid @RequestBody RegisterRequest req, HttpServletRequest http) {
        rateLimiter.check("register:" + ip(http), 10, 3600);
        auth.register(req.email(), req.password(), req.name());
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @PostMapping("/verify-email")
    public ResponseEntity<Void> verifyEmail(@Valid @RequestBody VerifyEmailRequest req) {
        auth.verifyEmail(req.email(), req.code());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/verify-email/resend")
    public ResponseEntity<Map<String, Boolean>> resendVerification(@Valid @RequestBody ForgotRequest req,
                                                                   HttpServletRequest http) {
        rateLimiter.check("verify-resend:" + ip(http), 5, 3600);
        boolean alreadyVerified = auth.resendVerificationEmail(req.email());
        return ResponseEntity.ok(Map.of("ok", true, "alreadyVerified", alreadyVerified));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthService.MeView> login(@Valid @RequestBody LoginRequest req,
                                                    HttpServletRequest http, HttpServletResponse res) {
        rateLimiter.check("login:" + ip(http) + ":" + req.email().toLowerCase(), 10, 300);
        AuthService.LoginResult result = auth.login(req.email(), req.password(), req.totp());
        setAuthCookies(res, result.tokens());
        return ResponseEntity.ok(result.me());
    }

    @PostMapping("/refresh")
    public ResponseEntity<Void> refresh(HttpServletRequest http, HttpServletResponse res) {
        String raw = cookies.read(http, cookies.refreshCookieName());
        if (raw == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "No session");
        setAuthCookies(res, auth.refresh(raw));
        return ResponseEntity.ok().build();
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest http, HttpServletResponse res) {
        auth.logout(cookies.read(http, cookies.refreshCookieName()));
        clearAuthCookies(res);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/logout-all")
    public ResponseEntity<Void> logoutAll(@AuthenticationPrincipal AuthPrincipal principal,
                                          HttpServletResponse res) {
        requireAuth(principal);
        auth.logoutAll(principal.userId());
        clearAuthCookies(res);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/me")
    public AuthService.MeView me(@AuthenticationPrincipal AuthPrincipal principal) {
        requireAuth(principal);
        return auth.me(principal.userId());
    }

    @PostMapping("/password/forgot")
    public ResponseEntity<Map<String, Boolean>> forgot(@Valid @RequestBody ForgotRequest req,
                                                       HttpServletRequest http) {
        rateLimiter.check("forgot:" + ip(http), 5, 3600);
        auth.forgotPassword(req.email());
        return ResponseEntity.ok(Map.of("ok", true)); // uniform — no oracle
    }

    @PostMapping("/password/reset")
    public ResponseEntity<Void> reset(@Valid @RequestBody ResetRequest req, HttpServletRequest http) {
        rateLimiter.check("reset:" + ip(http), 10, 3600);
        auth.resetPassword(req.email(), req.code(), req.newPassword());
        return ResponseEntity.ok().build();
    }

    // --- helpers ---

    private void setAuthCookies(HttpServletResponse res, AuthService.Tokens tokens) {
        res.addHeader(HttpHeaders.SET_COOKIE, cookies.access(tokens.accessToken()).toString());
        res.addHeader(HttpHeaders.SET_COOKIE, cookies.refresh(tokens.refreshToken()).toString());
    }

    private void clearAuthCookies(HttpServletResponse res) {
        res.addHeader(HttpHeaders.SET_COOKIE, cookies.clear(cookies.accessCookieName()).toString());
        res.addHeader(HttpHeaders.SET_COOKIE, cookies.clear(cookies.refreshCookieName()).toString());
    }

    private static void requireAuth(AuthPrincipal principal) {
        if (principal == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not authenticated");
    }

    private static String ip(HttpServletRequest request) {
        String fwd = request.getHeader("X-Forwarded-For");
        return (fwd != null && !fwd.isBlank()) ? fwd.split(",")[0].trim() : request.getRemoteAddr();
    }
}
