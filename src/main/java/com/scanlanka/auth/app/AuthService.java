package com.scanlanka.auth.app;

import com.scanlanka.auth.domain.AppUser;
import com.scanlanka.auth.domain.OneTimeCode.Purpose;
import com.scanlanka.auth.domain.Role;
import com.scanlanka.auth.domain.UserStatus;
import com.scanlanka.auth.infra.AppUserRepository;
import com.scanlanka.notification.app.NotificationService;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Auth orchestration (07-auth-accounts). Roles are server-minted; responses are uniform (no
 * account-existence oracle); reset/verify use OTP; logout-all bumps token_version.
 * Email delivery of OTPs is wired in Phase 6 (10-notifications) — codes are generated/stored now.
 */
@Service
public class AuthService {

    private static final int MAX_FAILED_LOGINS = 10;

    private final AppUserRepository users;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final RefreshTokenService refreshTokens;
    private final OneTimeCodeService otp;
    private final TotpService totp;
    private final NotificationService notifications;

    public AuthService(AppUserRepository users, PasswordEncoder encoder, JwtService jwt,
                       RefreshTokenService refreshTokens, OneTimeCodeService otp, TotpService totp,
                       NotificationService notifications) {
        this.users = users;
        this.encoder = encoder;
        this.jwt = jwt;
        this.refreshTokens = refreshTokens;
        this.otp = otp;
        this.totp = totp;
        this.notifications = notifications;
    }

    public record Tokens(String accessToken, String refreshToken) {}
    public record MeView(long id, String email, String name, String role, boolean emailVerified) {}
    public record LoginResult(Tokens tokens, MeView me) {}

    /** Register a CUSTOMER (role server-set). Uniform behaviour whether or not the email exists. */
    @Transactional
    public void register(String email, String rawPassword, String name) {
        if (!users.existsByEmailIgnoreCase(email)) {
            AppUser u = users.save(new AppUser(email.toLowerCase(), encoder.encode(rawPassword), name, Role.CUSTOMER));
            String code = otp.issue(u.getId(), Purpose.EMAIL_VERIFY);
            notifications.enqueue("EMAIL_VERIFY", u.getEmail(), "Verify your Scan Lanka email",
                "Your verification code is: " + code, "verify:" + u.getId() + ":" + code);
        }
        // else: do nothing, but respond identically (no enumeration oracle)
    }

    @Transactional
    public void verifyEmail(String email, String code) {
        AppUser u = users.findByEmailIgnoreCase(email).orElse(null);
        if (u != null && otp.verify(u.getId(), Purpose.EMAIL_VERIFY, code)) {
            u.setEmailVerified(true);
            users.save(u);
            return;
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid or expired code");
    }

    @Transactional
    public LoginResult login(String email, String rawPassword, String totpCode) {
        AppUser u = users.findByEmailIgnoreCase(email)
            .filter(x -> x.getStatus() == UserStatus.ACTIVE)
            .orElseThrow(AuthService::badCredentials);

        if (!encoder.matches(rawPassword, u.getPasswordHash())) {
            u.setFailedLogins(u.getFailedLogins() + 1);
            if (u.getFailedLogins() >= MAX_FAILED_LOGINS) u.setStatus(UserStatus.LOCKED);
            users.save(u);
            throw badCredentials();
        }
        if (u.getRole() == Role.ADMIN && u.isTotpEnabled() && !totp.verify(u.getTotpSecret(), totpCode)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "TOTP_REQUIRED");
        }
        u.setFailedLogins(0);
        users.save(u);
        return new LoginResult(issueTokens(u), toMeView(u));
    }

    @Transactional
    public Tokens refresh(String rawRefresh) {
        var rotation = refreshTokens.rotate(rawRefresh);
        AppUser u = users.findById(rotation.userId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid session"));
        return new Tokens(jwt.issueAccessToken(u), rotation.rawToken());
    }

    public void logout(String rawRefresh) {
        refreshTokens.revoke(rawRefresh);
    }

    @Transactional
    public void logoutAll(long userId) {
        AppUser u = users.findById(userId).orElseThrow(AuthService::badCredentials);
        u.bumpTokenVersion();          // invalidates all access tokens
        users.save(u);
        refreshTokens.revokeAllForUser(userId);
    }

    /** Always uniform — never reveals whether the email exists. */
    @Transactional
    public void forgotPassword(String email) {
        users.findByEmailIgnoreCase(email).ifPresent(u -> {
            String code = otp.issue(u.getId(), Purpose.PASSWORD_RESET);
            notifications.enqueue("PASSWORD_RESET", u.getEmail(), "Reset your Scan Lanka password",
                "Your password reset code is: " + code, "reset:" + u.getId() + ":" + code);
        });
    }

    @Transactional
    public void resetPassword(String email, String code, String newPassword) {
        AppUser u = users.findByEmailIgnoreCase(email).orElse(null);
        if (u != null && otp.verify(u.getId(), Purpose.PASSWORD_RESET, code)) {
            u.setPasswordHash(encoder.encode(newPassword));
            u.bumpTokenVersion();               // kill existing sessions
            users.save(u);
            refreshTokens.revokeAllForUser(u.getId());
            return;
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid or expired code");
    }

    public MeView me(long userId) {
        AppUser u = users.findById(userId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not authenticated"));
        return toMeView(u);
    }

    private static MeView toMeView(AppUser u) {
        return new MeView(u.getId(), u.getEmail(), u.getName(), u.getRole().name(), u.isEmailVerified());
    }

    private Tokens issueTokens(AppUser u) {
        return new Tokens(jwt.issueAccessToken(u), refreshTokens.issue(u.getId()));
    }

    private static ResponseStatusException badCredentials() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
    }
}
