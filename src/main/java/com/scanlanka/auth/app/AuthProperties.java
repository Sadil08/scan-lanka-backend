package com.scanlanka.auth.app;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Auth config (07-auth). Secret comes from env (global/02 §5); never commit a real value. */
@ConfigurationProperties(prefix = "app.auth")
public record AuthProperties(
    String jwtSecret,
    @DefaultValue("scanlanka") String issuer,
    @DefaultValue("scanlanka-web") String audience,
    @DefaultValue("15") long accessTtlMinutes,
    @DefaultValue("14") long refreshTtlDays,
    @DefaultValue("true") boolean cookieSecure,
    @DefaultValue("sl_at") String accessCookie,
    @DefaultValue("sl_rt") String refreshCookie,
    @DefaultValue("10") int otpTtlMinutes,
    @DefaultValue("5") int otpMaxAttempts,
    /** Optional — seeds the first ADMIN when no admin exists (07 T-15). */
    @DefaultValue("") String initialAdminEmail,
    @DefaultValue("") String initialAdminPassword
) {
}
