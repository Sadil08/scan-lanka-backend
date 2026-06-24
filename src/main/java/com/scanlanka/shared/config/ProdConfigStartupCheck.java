package com.scanlanka.shared.config;

import com.scanlanka.auth.app.AuthProperties;
import com.scanlanka.order.app.OrderProperties;
import com.scanlanka.payment.app.PayHereProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Fail-fast guard for production misconfiguration (P0-1, P0-2, P2-2, P2-3). Active only under the
 * {@code prod} profile, so local/dev defaults stay relaxed. If any prod-critical security setting is
 * missing or still on its dev default, the app refuses to start rather than serving traffic insecurely.
 */
@Component
@Profile("prod")
public class ProdConfigStartupCheck implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ProdConfigStartupCheck.class);

    private final AuthProperties auth;
    private final PayHereProperties payhere;
    private final OrderProperties order;
    private final String storageDir;

    public ProdConfigStartupCheck(AuthProperties auth, PayHereProperties payhere, OrderProperties order,
                                  @Value("${app.storage.dir}") String storageDir) {
        this.auth = auth;
        this.payhere = payhere;
        this.order = order;
        this.storageDir = storageDir;
    }

    @Override
    public void run(ApplicationArguments args) {
        List<String> problems = new ArrayList<>();

        // P0-2: admin 2FA must be enforced in production.
        if (!auth.adminTotpRequired()) {
            problems.add("ADMIN_TOTP_REQUIRED must be true in production");
        }
        // P0-1: auth cookies must carry the Secure flag (SameSite=Strict is only reliable with Secure).
        if (!auth.cookieSecure()) {
            problems.add("AUTH_COOKIE_SECURE must be true in production");
        }
        // Secrets must be real, not the committed dev defaults.
        if (isBlankOrDefault(auth.jwtSecret(), "dev-only-change-me-please-rotate-32+bytes-secret")) {
            problems.add("JWT_SECRET must be set to a real secret in production");
        }
        if (isBlankOrDefault(order.signingSecret(), "dev-only-order-signing-secret-change-me")) {
            problems.add("ORDER_SIGNING_SECRET must be set to a real secret in production");
        }
        if (isBlank(payhere.merchantId()) || isBlank(payhere.merchantSecret())) {
            problems.add("PAYHERE_MERCHANT_ID and PAYHERE_MERCHANT_SECRET must be set in production");
        }
        // P2-2: a localhost notify URL means PayHere webhooks never arrive → orders stuck PENDING.
        if (isBlank(payhere.notifyUrl()) || isLocalhost(payhere.notifyUrl())) {
            problems.add("PAYHERE_NOTIFY_URL must be a public https URL in production (not localhost)");
        }
        // P2-3: a relative storage dir breaks if the JVM working directory changes.
        if (isBlank(storageDir) || !Path.of(storageDir).isAbsolute()) {
            problems.add("STORAGE_DIR must be an absolute path in production");
        }

        if (!problems.isEmpty()) {
            throw new IllegalStateException("Production configuration invalid:\n  - "
                + String.join("\n  - ", problems));
        }
        log.info("Production configuration check passed ({} settings verified)", 6);
    }

    private static boolean isBlank(String v) {
        return v == null || v.isBlank();
    }

    private static boolean isBlankOrDefault(String v, String devDefault) {
        return isBlank(v) || v.equals(devDefault);
    }

    private static boolean isLocalhost(String url) {
        String u = url.toLowerCase();
        return u.contains("localhost") || u.contains("127.0.0.1") || u.contains("://0.0.0.0");
    }
}
