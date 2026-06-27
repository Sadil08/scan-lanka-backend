package com.scanlanka.notification;

import com.scanlanka.notification.app.EmailProvider;
import com.scanlanka.notification.app.LoggingEmailProvider;
import com.scanlanka.notification.app.SmtpEmailProvider;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mail.javamail.JavaMailSender;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Verifies the provider-selection wiring (10 D-Q1): {@link SmtpEmailProvider} is gated on
 * {@code app.notifications.smtp-enabled} and, when present, is {@link org.springframework.context.annotation.Primary}
 * so it wins over the dev {@link LoggingEmailProvider}. Uses {@link ApplicationContextRunner} — no DB / Testcontainers.
 */
class SmtpEmailProviderWiringTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withBean(JavaMailSender.class, () -> mock(JavaMailSender.class))
        .withUserConfiguration(LoggingEmailProvider.class, SmtpEmailProvider.class)
        .withPropertyValues("app.notifications.from-email=Scan Lanka <no-reply@scanlanka.com>");

    @Test
    void smtpProviderIsPrimaryWhenEnabled() {
        runner.withPropertyValues("app.notifications.smtp-enabled=true").run(ctx -> {
            assertThat(ctx).hasSingleBean(SmtpEmailProvider.class);
            assertThat(ctx).hasSingleBean(LoggingEmailProvider.class);
            // @Primary means an unqualified EmailProvider injection resolves to SMTP.
            assertThat(ctx.getBean(EmailProvider.class)).isInstanceOf(SmtpEmailProvider.class);
        });
    }

    @Test
    void loggingProviderUsedWhenPropertyMissing() {
        runner.run(ctx -> {
            assertThat(ctx).doesNotHaveBean(SmtpEmailProvider.class);
            assertThat(ctx.getBean(EmailProvider.class)).isInstanceOf(LoggingEmailProvider.class);
        });
    }

    @Test
    void loggingProviderUsedWhenExplicitlyDisabled() {
        runner.withPropertyValues("app.notifications.smtp-enabled=false").run(ctx -> {
            assertThat(ctx).doesNotHaveBean(SmtpEmailProvider.class);
            assertThat(ctx.getBean(EmailProvider.class)).isInstanceOf(LoggingEmailProvider.class);
        });
    }
}
