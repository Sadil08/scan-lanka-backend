package com.scanlanka.auth.app;

import com.scanlanka.auth.infra.AppUserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/** Step-up re-auth for high-risk admin actions (07 FR-AUTH-14, 16 SEC-RETURN-1). */
@Service
public class StepUpService {

    private final AppUserRepository users;
    private final PasswordEncoder encoder;
    private final TotpService totp;

    public StepUpService(AppUserRepository users, PasswordEncoder encoder, TotpService totp) {
        this.users = users;
        this.encoder = encoder;
        this.totp = totp;
    }

    public record StepUpCredentials(String password, String totp) {}

    public void require(long adminId, StepUpCredentials creds) {
        if (creds == null) throw stepUpRequired();
        var admin = users.findById(adminId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not authenticated"));
        if (creds.password() != null && !creds.password().isBlank()
            && encoder.matches(creds.password(), admin.getPasswordHash())) {
            return;
        }
        if (admin.isTotpEnabled() && creds.totp() != null && totp.verify(admin.getTotpSecret(), creds.totp())) {
            return;
        }
        throw stepUpRequired();
    }

    private static ResponseStatusException stepUpRequired() {
        return new ResponseStatusException(HttpStatus.FORBIDDEN, "STEP_UP_REQUIRED");
    }
}
