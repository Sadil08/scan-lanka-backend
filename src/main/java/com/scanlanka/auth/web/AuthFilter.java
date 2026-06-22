package com.scanlanka.auth.web;

import com.scanlanka.auth.app.JwtService;
import com.scanlanka.auth.domain.AppUser;
import com.scanlanka.auth.domain.UserStatus;
import com.scanlanka.auth.infra.AppUserRepository;
import com.scanlanka.shared.security.AuthPrincipal;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Authenticates each request from the httpOnly access cookie (global/02 §2). Validates signature +
 * iss/aud/exp (JwtService) and **token_version** against the user (so logout-all / reset takes effect),
 * and that the account is ACTIVE. On any problem the request proceeds unauthenticated (→ 401/403 on
 * protected routes). The per-transaction RLS GUC ({@code app.current_user_id}) is bound by
 * {@link com.scanlanka.shared.security.RlsGucAspect} on every {@code @Transactional} call.
 */
@Component
public class AuthFilter extends OncePerRequestFilter {

    private final JwtService jwt;
    private final AppUserRepository users;
    private final CookieFactory cookies;

    public AuthFilter(JwtService jwt, AppUserRepository users, CookieFactory cookies) {
        this.jwt = jwt;
        this.users = users;
        this.cookies = cookies;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String token = cookies.read(request, cookies.accessCookieName());
        if (token != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                JwtService.AccessClaims claims = jwt.parse(token);
                AppUser u = users.findById(claims.userId()).orElse(null);
                if (u != null && u.getStatus() == UserStatus.ACTIVE
                    && claims.tokenVersion() == u.getTokenVersion()) {
                    var principal = new AuthPrincipal(u.getId(), u.getRole().name());
                    var auth = new UsernamePasswordAuthenticationToken(
                        principal, null, List.of(new SimpleGrantedAuthority("ROLE_" + u.getRole().name())));
                    SecurityContextHolder.getContext().setAuthentication(auth);
                }
            } catch (JwtService.InvalidTokenException ignored) {
                // leave unauthenticated
            }
        }
        chain.doFilter(request, response);
    }
}
