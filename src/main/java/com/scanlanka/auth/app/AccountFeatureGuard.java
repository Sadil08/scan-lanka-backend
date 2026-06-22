package com.scanlanka.auth.app;

import com.scanlanka.auth.infra.AppUserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * Gates customer account features behind email verification (07 FR-AUTH-11). Guest checkout and
 * public cart validation are unaffected.
 */
@Component
public class AccountFeatureGuard {

    private final AppUserRepository users;

    public AccountFeatureGuard(AppUserRepository users) {
        this.users = users;
    }

    public void requireVerifiedEmail(long userId) {
        users.findById(userId).ifPresent(u -> {
            if (!u.isEmailVerified()) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "EMAIL_NOT_VERIFIED");
            }
        });
    }
}
