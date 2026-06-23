package com.scanlanka.contact.app;

import com.scanlanka.contact.domain.ContactInquiry;
import com.scanlanka.contact.infra.ContactInquiryRepository;
import com.scanlanka.geo.app.GeoService;
import com.scanlanka.notification.app.NotificationService;
import com.scanlanka.shared.text.HtmlEscaper;
import com.scanlanka.shared.text.TextSanitizer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class ContactService {

    private final ContactInquiryRepository repo;
    private final NotificationService notifications;
    private final GeoService geo;
    private final String adminEmail;

    public ContactService(ContactInquiryRepository repo, NotificationService notifications,
                          GeoService geo, @Value("${app.notifications.admin-email}") String adminEmail) {
        this.repo = repo;
        this.notifications = notifications;
        this.geo = geo;
        this.adminEmail = adminEmail;
    }

    public record SubmitRequest(String name, String email, String phone, String message) {}
    public record InquiryView(long id, String name, String email, String phone, String message,
                              String status, Instant createdAt) {}
    public record WhatsAppView(String number, String prefill) {}

    @Transactional
    public InquiryView submit(SubmitRequest req) {
        String name = TextSanitizer.plain(req.name(), 160, "NAME");
        String email = TextSanitizer.email(req.email());
        String phone = TextSanitizer.optional(req.phone(), 32);
        String message = TextSanitizer.plain(req.message(), 4000, "MESSAGE");
        ContactInquiry inq = repo.save(new ContactInquiry(name, email, phone, message));
        notifications.enqueue("CONTACT_INQUIRY", adminEmail,
            HtmlEscaper.subject("Contact inquiry from " + name),
            "<p><strong>" + HtmlEscaper.escape(name) + "</strong> (" + HtmlEscaper.escape(email) + ")</p>"
                + "<p>" + HtmlEscaper.escape(message) + "</p>",
            "contact:" + inq.getId());
        return toView(inq);
    }

    @Transactional(readOnly = true)
    public List<InquiryView> list(String status) {
        String st = status == null || status.isBlank() ? "NEW" : status.toUpperCase();
        return repo.findByStatusOrderByCreatedAtDesc(st).stream().map(this::toView).toList();
    }

    @Transactional
    public void markHandled(long id) {
        ContactInquiry inq = repo.findById(id).orElseThrow();
        inq.markHandled();
        repo.save(inq);
    }

    public WhatsAppView whatsapp(String ip, String countryOverride, String context) {
        var ctx = geo.resolve(ip, countryOverride);
        String prefill = context != null && !context.isBlank()
            ? context
            : (ctx.isSriLanka()
                ? "Hi Scan Lanka, I have a question."
                : "Hi Scan Lanka, I'd like a quote for bulk/international order.");
        return new WhatsAppView(ctx.whatsappNumber(), prefill);
    }

    private InquiryView toView(ContactInquiry i) {
        return new InquiryView(i.getId(), i.getName(), i.getEmail(), i.getPhone(),
            i.getMessage(), i.getStatus(), i.getCreatedAt());
    }
}
