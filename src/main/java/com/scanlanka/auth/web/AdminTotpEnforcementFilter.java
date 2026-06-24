package com.scanlanka.auth.web;

import com.scanlanka.auth.app.AuthProperties;
import com.scanlanka.auth.domain.AppUser;
import com.scanlanka.auth.infra.AppUserRepository;
import com.scanlanka.shared.security.AuthPrincipal;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Blocks {@code /api/admin/**} for ADMIN accounts that have not enrolled TOTP yet (07 FR-AUTH-10).
 * Allows {@code /api/auth/2fa/**} so enrolment can complete after first login.
 */
@Component
public class AdminTotpEnforcementFilter extends OncePerRequestFilter {

    private final AppUserRepository users;
    private final AuthProperties props;

    public AdminTotpEnforcementFilter(AppUserRepository users, AuthProperties props) {
        this.users = users;
        this.props = props;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // Skip entirely when admin TOTP is not required (dev), or for non-admin paths.
        return !props.adminTotpRequired() || !request.getRequestURI().startsWith("/api/admin/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AuthPrincipal principal
            && "ADMIN".equals(principal.role())) {
            AppUser admin = users.findById(principal.userId()).orElse(null);
            if (admin != null && !admin.isTotpEnabled()) {
                response.sendError(HttpStatus.FORBIDDEN.value(), "TOTP_ENROLL_REQUIRED");
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
