package com.scanlanka.auth.web;

import com.scanlanka.auth.app.AuthProperties;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.util.Arrays;

/**
 * Builds httpOnly, Secure, SameSite=Strict auth cookies (global/02 §2, 07 FR-AUTH-3) — tokens are
 * never JS-readable. SameSite=Strict also gives CSRF protection for our same-site SPA.
 */
@Component
public class CookieFactory {

    private final AuthProperties props;

    public CookieFactory(AuthProperties props) {
        this.props = props;
    }

    public ResponseCookie access(String token) {
        return base(props.accessCookie(), token, props.accessTtlMinutes() * 60).build();
    }

    public ResponseCookie refresh(String token) {
        return base(props.refreshCookie(), token, props.refreshTtlDays() * 86_400).build();
    }

    public ResponseCookie clear(String name) {
        return base(name, "", 0).build();
    }

    public String accessCookieName() {
        return props.accessCookie();
    }

    public String refreshCookieName() {
        return props.refreshCookie();
    }

    public String read(HttpServletRequest request, String name) {
        if (request.getCookies() == null) return null;
        return Arrays.stream(request.getCookies())
            .filter(c -> c.getName().equals(name))
            .map(jakarta.servlet.http.Cookie::getValue)
            .findFirst()
            .orElse(null);
    }

    private ResponseCookie.ResponseCookieBuilder base(String name, String value, long maxAgeSeconds) {
        return ResponseCookie.from(name, value)
            .httpOnly(true)
            .secure(props.cookieSecure())
            .sameSite("Strict")
            .path("/")
            .maxAge(maxAgeSeconds);
    }
}
