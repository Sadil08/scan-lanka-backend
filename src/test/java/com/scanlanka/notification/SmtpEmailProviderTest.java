package com.scanlanka.notification;

import com.scanlanka.notification.app.SmtpEmailProvider;
import jakarta.mail.Address;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit test for the real SMTP adapter (10 D-Q1). No network: {@link JavaMailSender} is mocked, so we
 * assert the {@link MimeMessage} is built correctly (from/to/subject/HTML) and that every failure mode
 * surfaces as a thrown exception — that is the contract {@code NotificationWorker} relies on to retry.
 */
class SmtpEmailProviderTest {

    private static final String FROM = "Scan Lanka <no-reply@scanlanka.com>";

    @Test
    void buildsHtmlMessageWithFromToSubjectAndSends() throws Exception {
        JavaMailSender sender = mock(JavaMailSender.class);
        // Real MimeMessage so MimeMessageHelper actually populates it; we then inspect what was sent.
        when(sender.createMimeMessage()).thenReturn(new MimeMessage((jakarta.mail.Session) null));
        SmtpEmailProvider provider = new SmtpEmailProvider(sender, FROM);

        provider.send("buyer@example.lk", "Your Scan Lanka receipt", "<p>Thanks for your order</p>");

        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(sender).send(captor.capture());
        MimeMessage sent = captor.getValue();

        assertThat(sent.getFrom()[0].toString()).contains("no-reply@scanlanka.com");
        assertThat(sent.getAllRecipients()[0].toString()).isEqualTo("buyer@example.lk");
        assertThat(sent.getSubject()).isEqualTo("Your Scan Lanka receipt");
        // getContentType() reads the MIME header, which is only finalized by saveChanges() during a real
        // transport send (mocked here); the DataHandler carries the true content type set by setText(.., true).
        assertThat(sent.getDataHandler().getContentType()).contains("text/html");
        assertThat(sent.getContent().toString()).contains("<p>Thanks for your order</p>");
    }

    @Test
    void wrapsMessagingExceptionDuringBuildSoWorkerCanRetry() throws Exception {
        JavaMailSender sender = mock(JavaMailSender.class);
        MimeMessage broken = mock(MimeMessage.class);
        when(sender.createMimeMessage()).thenReturn(broken);
        // Simulate a bad address: setting the recipient throws the checked MessagingException.
        doThrow(new MessagingException("bad recipient"))
            .when(broken).setRecipient(any(Message.RecipientType.class), any(Address.class));
        SmtpEmailProvider provider = new SmtpEmailProvider(sender, FROM);

        assertThatThrownBy(() -> provider.send("buyer@example.lk", "Subj", "<p>x</p>"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("SMTP send failed");

        // Build failed before transmission — nothing was handed to the transport.
        verify(sender, never()).send(any(MimeMessage.class));
    }

    @Test
    void propagatesTransportFailureSoWorkerCanRetry() {
        JavaMailSender sender = mock(JavaMailSender.class);
        when(sender.createMimeMessage()).thenReturn(new MimeMessage((jakarta.mail.Session) null));
        // SMTP server unreachable / auth rejected at send time.
        doThrow(new MailSendException("connection refused")).when(sender).send(any(MimeMessage.class));
        SmtpEmailProvider provider = new SmtpEmailProvider(sender, FROM);

        // Must throw (not swallow) so the worker counts an attempt and backs off.
        assertThatThrownBy(() -> provider.send("buyer@example.lk", "Subj", "<p>x</p>"))
            .isInstanceOf(RuntimeException.class);
    }
}
