package com.scanlanka.support.app;

import com.scanlanka.auth.domain.AppUser;
import com.scanlanka.auth.infra.AppUserRepository;
import com.scanlanka.notification.app.NotificationService;
import com.scanlanka.shared.text.HtmlEscaper;
import com.scanlanka.shared.text.TextSanitizer;
import com.scanlanka.support.domain.SupportConversation;
import com.scanlanka.support.domain.SupportMessage;
import com.scanlanka.support.domain.SupportMessage.Sender;
import com.scanlanka.support.infra.SupportConversationRepository;
import com.scanlanka.support.infra.SupportMessageRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;

@Service
public class SupportChatService {

    private final SupportConversationRepository conversations;
    private final SupportMessageRepository messages;
    private final SupportTokenService tokens;
    private final NotificationService notifications;
    private final AppUserRepository users;
    private final String adminEmail;

    public SupportChatService(SupportConversationRepository conversations,
                              SupportMessageRepository messages,
                              SupportTokenService tokens,
                              NotificationService notifications,
                              AppUserRepository users,
                              @Value("${app.notifications.admin-email}") String adminEmail) {
        this.conversations = conversations;
        this.messages = messages;
        this.tokens = tokens;
        this.notifications = notifications;
        this.users = users;
        this.adminEmail = adminEmail;
    }

    public record StartRequest(String name, String email, String message, String pageContext) {}
    public record MessageBody(String body) {}

    public record MessageView(long id, String sender, String body, Instant at) {}
    public record ConversationView(long id, String status, String visitorName, String visitorEmail,
                                   Long customerId, String pageContext, Instant updatedAt,
                                   List<MessageView> messages) {}
    public record StartResult(String accessToken, ConversationView conversation) {}
    public record SummaryView(long id, String status, String visitorName, String visitorEmail,
                              Long customerId, String preview, Instant updatedAt) {}

    @Transactional
    public StartResult start(StartRequest req, Long customerId) {
        String message = TextSanitizer.plain(req.message(), 2000, "MESSAGE");
        String name = TextSanitizer.optional(req.name(), 160);
        String email = req.email() != null && !req.email().isBlank()
            ? TextSanitizer.email(req.email()) : null;
        if (customerId != null) {
            AppUser account = users.findById(customerId).orElse(null);
            if (account != null) {
                if (account.getName() != null && !account.getName().isBlank()) {
                    name = TextSanitizer.optional(account.getName(), 160);
                }
                email = account.getEmail();
            }
        }
        String pageContext = TextSanitizer.optional(req.pageContext(), 500);

        String token = tokens.issue();
        SupportConversation conv = conversations.save(new SupportConversation(
            tokens.hash(token), name, email, customerId, pageContext));
        SupportMessage first = messages.save(new SupportMessage(conv.getId(), Sender.VISITOR, message, null));
        conv.touch();
        conversations.save(conv);

        notifyAdmin(conv, name, email, message);
        return new StartResult(token, toDetail(conv, List.of(first)));
    }

    @Transactional(readOnly = true)
    public ConversationView getByToken(String token) {
        SupportConversation conv = byToken(token);
        return toDetail(conv, messages.findByConversationIdOrderByCreatedAtAsc(conv.getId()));
    }

    @Transactional
    public ConversationView visitorMessage(String token, String body) {
        SupportConversation conv = byToken(token);
        ensureOpen(conv);
        String text = TextSanitizer.plain(body, 2000, "MESSAGE");
        messages.save(new SupportMessage(conv.getId(), Sender.VISITOR, text, null));
        conv.touch();
        conversations.save(conv);
        return toDetail(conv, messages.findByConversationIdOrderByCreatedAtAsc(conv.getId()));
    }

    @Transactional(readOnly = true)
    public List<SummaryView> adminList(String status) {
        String st = status == null || status.isBlank() ? "OPEN" : status.toUpperCase();
        return conversations.findByStatusOrderByUpdatedAtDesc(st).stream().map(this::toSummary).toList();
    }

    @Transactional(readOnly = true)
    public ConversationView adminDetail(long id) {
        SupportConversation conv = load(id);
        return toDetail(conv, messages.findByConversationIdOrderByCreatedAtAsc(conv.getId()));
    }

    @Transactional
    public ConversationView adminMessage(long id, long adminUserId, String body) {
        SupportConversation conv = load(id);
        ensureOpen(conv);
        String text = TextSanitizer.plain(body, 2000, "MESSAGE");
        messages.save(new SupportMessage(conv.getId(), Sender.ADMIN, text, adminUserId));
        conv.touch();
        conversations.save(conv);
        return toDetail(conv, messages.findByConversationIdOrderByCreatedAtAsc(conv.getId()));
    }

    @Transactional
    public void close(long id) {
        SupportConversation conv = load(id);
        conv.close();
        conversations.save(conv);
    }

    private void notifyAdmin(SupportConversation conv, String name, String email, String message) {
        String who = name != null && !name.isBlank() ? name : "A visitor";
        notifications.enqueue("SUPPORT_CHAT", adminEmail,
            HtmlEscaper.subject("Customer care chat from " + who),
            "<p><strong>" + HtmlEscaper.escape(who) + "</strong>"
                + (email != null ? " (" + HtmlEscaper.escape(email) + ")" : "") + "</p>"
                + "<p>" + HtmlEscaper.escape(message) + "</p>",
            "support:" + conv.getId());
    }

    private SupportConversation byToken(String token) {
        return conversations.findByAccessTokenHash(tokens.hash(token))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "NOT_FOUND"));
    }

    private SupportConversation load(long id) {
        return conversations.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "NOT_FOUND"));
    }

    private static void ensureOpen(SupportConversation conv) {
        if (!conv.isOpen()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "CLOSED");
        }
    }

    private SummaryView toSummary(SupportConversation conv) {
        List<SupportMessage> msgs = messages.findByConversationIdOrderByCreatedAtAsc(conv.getId());
        String preview = msgs.isEmpty() ? "" : msgs.get(msgs.size() - 1).getBody();
        if (preview.length() > 120) {
            preview = preview.substring(0, 117) + "...";
        }
        return new SummaryView(conv.getId(), conv.getStatus(), conv.getVisitorName(),
            conv.getVisitorEmail(), conv.getCustomerId(), preview, conv.getUpdatedAt());
    }

    private ConversationView toDetail(SupportConversation conv, List<SupportMessage> msgs) {
        return new ConversationView(conv.getId(), conv.getStatus(), conv.getVisitorName(),
            conv.getVisitorEmail(), conv.getCustomerId(), conv.getPageContext(), conv.getUpdatedAt(),
            msgs.stream().map(m -> new MessageView(m.getId(), m.getSender(), m.getBody(), m.getCreatedAt())).toList());
    }
}
