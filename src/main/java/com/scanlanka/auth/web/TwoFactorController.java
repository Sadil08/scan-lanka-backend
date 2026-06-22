package com.scanlanka.auth.web;

import com.scanlanka.auth.app.TotpService;
import com.scanlanka.auth.domain.AppUser;
import com.scanlanka.auth.infra.AppUserRepository;
import com.scanlanka.shared.security.AuthPrincipal;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/** Admin TOTP 2FA enrolment (07-auth §3). Must complete before {@code /api/admin/**} access. */
@RestController
@RequestMapping("/api/auth/2fa")
public class TwoFactorController {

    private final AppUserRepository users;
    private final TotpService totp;

    public TwoFactorController(AppUserRepository users, TotpService totp) {
        this.users = users;
        this.totp = totp;
    }

    public record SetupResponse(String secret, String otpauthUrl) {}
    public record EnableRequest(@NotBlank String totp) {}

    @PostMapping("/setup")
    public SetupResponse setup(@AuthenticationPrincipal AuthPrincipal principal) {
        AppUser admin = requireAdmin(principal);
        if (admin.isTotpEnabled()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "TOTP_ALREADY_ENABLED");
        }
        String secret = totp.generateSecret();
        admin.setTotpSecret(secret);
        users.save(admin);
        String label = URLEncoder.encode("ScanLanka:" + admin.getEmail(), StandardCharsets.UTF_8);
        String url = "otpauth://totp/" + label + "?secret=" + secret + "&issuer=ScanLanka";
        return new SetupResponse(secret, url);
    }

    @PostMapping("/enable")
    public void enable(@AuthenticationPrincipal AuthPrincipal principal,
                       @RequestBody EnableRequest req) {
        AppUser admin = requireAdmin(principal);
        if (admin.getTotpSecret() == null || admin.getTotpSecret().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "TOTP_NOT_SETUP");
        }
        if (!totp.verify(admin.getTotpSecret(), req.totp())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_TOTP");
        }
        admin.setTotpEnabled(true);
        users.save(admin);
    }

    private AppUser requireAdmin(AuthPrincipal principal) {
        if (principal == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not authenticated");
        }
        if (!"ADMIN".equals(principal.role())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "ADMIN_ONLY");
        }
        return users.findById(principal.userId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not authenticated"));
    }
}
