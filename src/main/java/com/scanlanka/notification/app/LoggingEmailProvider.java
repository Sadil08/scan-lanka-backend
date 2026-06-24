package com.scanlanka.notification.app;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Dev email provider — logs instead of sending (D-Q1: real SMTP/SendGrid/SES TBD). Recipient is
 * redacted in logs (NFR-NOTIFY-2). Replace with a real adapter behind {@link EmailProvider}.
 */
@Component
public class LoggingEmailProvider implements EmailProvider {

    private static final Logger log = LoggerFactory.getLogger(LoggingEmailProvider.class);

    @Override
    public void send(String to, String subject, String body) {
        // Dev provider: log the full body so OTP/reset codes are visible locally (no real SMTP yet).
        log.info("[email] to={} subject=\"{}\"\n{}", redact(to), subject, body);
    }

    private static String redact(String email) {
        if (email == null) return "?";
        int at = email.indexOf('@');
        if (at <= 1) return "***" + (at >= 0 ? email.substring(at) : "");
        return email.charAt(0) + "***" + email.substring(at);
    }
}
