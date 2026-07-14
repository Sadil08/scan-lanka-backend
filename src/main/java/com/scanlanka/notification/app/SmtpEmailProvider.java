package com.scanlanka.notification.app;

import com.scanlanka.shared.branding.BrandAssets;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

/**
 * Real SMTP delivery behind {@link EmailProvider} (D-Q1). Active only when
 * {@code app.notifications.smtp-enabled=true} and {@code spring.mail.*} is configured — all
 * credentials come from env, never committed (global/02 §5). Bodies are HTML
 * ({@link EmailTemplateRenderer}). Marked {@link Primary} so it wins over the dev
 * {@link LoggingEmailProvider} whenever it is present; otherwise the logging provider is used.
 *
 * <p>The brand logo is attached as an inline CID part so clients that block {@code data:} images
 * (Gmail, etc.) still show the letterhead. Send failures throw, so {@link NotificationWorker}'s
 * retry/backoff/dead-letter logic applies.
 */
@Component
@Primary
@ConditionalOnProperty(prefix = "app.notifications", name = "smtp-enabled", havingValue = "true")
public class SmtpEmailProvider implements EmailProvider {

    private final JavaMailSender sender;
    private final String from;

    public SmtpEmailProvider(JavaMailSender sender,
                             @Value("${app.notifications.from-email}") String from) {
        this.sender = sender;
        this.from = from;
    }

    @Override
    public void send(String to, String subject, String body) {
        try {
            MimeMessage message = sender.createMimeMessage();
            // multipart=true so we can attach the logo as an inline CID image
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(from);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(body, true); // HTML body
            byte[] logo = BrandAssets.LOGO_PNG_BYTES;
            if (logo.length > 0) {
                helper.addInline(BrandAssets.LOGO_CID, new ByteArrayResource(logo) {
                    @Override
                    public String getFilename() {
                        return "logo.png";
                    }
                }, "image/png");
            }
            sender.send(message);
        } catch (MessagingException e) {
            // Wrap the checked exception so the worker's catch(Exception) retry path handles it.
            throw new IllegalStateException("SMTP send failed: " + e.getMessage(), e);
        }
    }
}
