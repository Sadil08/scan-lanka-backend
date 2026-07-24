package com.scanlanka.notification.app;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Logs once at startup whether real email delivery is on, so "why didn't the buyer get a mail?" is a
 * two-second check of the boot log rather than a guess. When {@code MAIL_ENABLED=true}, the @Primary
 * {@link SmtpEmailProvider} is wired; otherwise {@link LoggingEmailProvider} only logs emails and
 * nothing is actually delivered.
 */
@Component
public class EmailDeliveryStartupLog {

    private static final Logger log = LoggerFactory.getLogger(EmailDeliveryStartupLog.class);

    private final EmailProvider provider;

    public EmailDeliveryStartupLog(EmailProvider provider) {
        this.provider = provider;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void report() {
        if (provider instanceof SmtpEmailProvider) {
            log.info("Email delivery: SMTP ENABLED — order/receipt emails will be sent.");
        } else {
            log.warn("Email delivery: LOG-ONLY ({}). Emails are only logged, NOT sent. "
                + "Set MAIL_ENABLED=true (plus MAIL_HOST/MAIL_USERNAME/MAIL_PASSWORD/MAIL_FROM) to deliver real email.",
                provider.getClass().getSimpleName());
        }
    }
}
