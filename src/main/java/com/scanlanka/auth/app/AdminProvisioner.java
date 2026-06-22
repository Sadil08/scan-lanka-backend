package com.scanlanka.auth.app;

import com.scanlanka.auth.domain.AppUser;
import com.scanlanka.auth.domain.Role;
import com.scanlanka.auth.infra.AppUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Seeds the first ADMIN from env when none exists (07-auth T-15, Q-3). Idempotent — safe on every boot.
 */
@Component
public class AdminProvisioner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminProvisioner.class);

    private final AppUserRepository users;
    private final PasswordEncoder encoder;
    private final AuthProperties props;

    public AdminProvisioner(AppUserRepository users, PasswordEncoder encoder, AuthProperties props) {
        this.users = users;
        this.encoder = encoder;
        this.props = props;
    }

    @Override
    public void run(ApplicationArguments args) {
        provisionIfNeeded();
    }

    void provisionIfNeeded() {
        String email = props.initialAdminEmail();
        String password = props.initialAdminPassword();
        if (email == null || email.isBlank() || password == null || password.isBlank()) {
            return;
        }
        if (users.existsByRole(Role.ADMIN)) {
            return;
        }
        if (users.existsByEmailIgnoreCase(email)) {
            log.warn("initial admin email already registered as non-admin — skipping provision");
            return;
        }
        AppUser admin = new AppUser(
            email.toLowerCase().trim(), encoder.encode(password), "Admin", Role.ADMIN);
        admin.setEmailVerified(true);
        users.save(admin);
        log.info("provisioned initial ADMIN account for {}", email);
    }
}
