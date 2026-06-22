package com.scanlanka.shared.security;

import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Binds {@code app.current_user_id} at the start of every {@code @Transactional} service call when
 * the request is authenticated (07-auth T-8, SEC-AUTH-1). Works with connection pooling because the
 * GUC is transaction-local ({@link RlsContext}). Fail-closed: a JDBC failure aborts the transaction.
 */
@Aspect
@Component
@Order(0)
public class RlsGucAspect {

    private final RlsContext rls;

    public RlsGucAspect(RlsContext rls) {
        this.rls = rls;
    }

    @Before("@annotation(org.springframework.transaction.annotation.Transactional) || " +
            "@within(org.springframework.transaction.annotation.Transactional)")
    public void bindRlsGuc() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AuthPrincipal principal) {
            rls.setCurrentUser(principal.userId());
        }
    }
}
