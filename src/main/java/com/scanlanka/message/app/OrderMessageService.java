package com.scanlanka.message.app;

import com.scanlanka.admin.app.AuditService;
import com.scanlanka.message.domain.MessageThread;
import com.scanlanka.message.domain.OrderMessage;
import com.scanlanka.message.domain.OrderMessage.AuthorRole;
import com.scanlanka.message.infra.MessageThreadRepository;
import com.scanlanka.message.infra.OrderMessageRepository;
import com.scanlanka.notification.app.NotificationService;
import com.scanlanka.order.app.OrderNumberService;
import com.scanlanka.order.domain.Order;
import com.scanlanka.order.infra.OrderItemRepository;
import com.scanlanka.order.infra.OrderRepository;
import com.scanlanka.shared.text.HtmlEscaper;
import com.scanlanka.shared.text.TextSanitizer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;

@Service
public class OrderMessageService {

    private final MessageThreadRepository threads;
    private final OrderMessageRepository messages;
    private final OrderRepository orders;
    private final OrderItemRepository orderItems;
    private final OrderNumberService orderNumbers;
    private final OrderMessageTokenService tokens;
    private final NotificationService notifications;
    private final AuditService audit;
    private final String adminEmail;
    private final String frontendBase;

    public OrderMessageService(MessageThreadRepository threads, OrderMessageRepository messages,
                               OrderRepository orders, OrderItemRepository orderItems,
                               OrderNumberService orderNumbers, OrderMessageTokenService tokens,
                               NotificationService notifications, AuditService audit,
                               @Value("${app.notifications.admin-email}") String adminEmail,
                               @Value("${app.frontend-base-url}") String frontendBase) {
        this.threads = threads;
        this.messages = messages;
        this.orders = orders;
        this.orderItems = orderItems;
        this.orderNumbers = orderNumbers;
        this.tokens = tokens;
        this.notifications = notifications;
        this.audit = audit;
        this.adminEmail = adminEmail;
        this.frontendBase = frontendBase;
    }

    public record MessageView(long id, String role, String label, String body, Instant at) {}
    public record MessageBody(String body) {}
    public record ThreadView(long id, String orderNumber, String status, int unread,
                             List<MessageView> messages) {}
    public record ThreadSummary(long id, String orderNumber, String status, int adminUnread,
                                String preview, Instant lastMessageAt) {}
    public record OrderLineView(String name, String sku, int quantity, long lineTotalCents) {}
    public record LinkedOrderView(String orderNumber, String status, String contactName,
                                  String contactEmail, List<OrderLineView> lines) {}
    public record AdminThreadView(long id, String orderNumber, String status, int adminUnread,
                                  List<MessageView> messages, LinkedOrderView order) {}
    public record TokenResult(String accessToken, ThreadView thread) {}

    @Transactional
    public MessageThread openForOrder(long orderId) {
        return threads.findByOrderId(orderId)
            .orElseGet(() -> threads.save(MessageThread.openFor(orderId)));
    }

    @Transactional
    public ThreadView forCustomer(long customerId, String orderNumber) {
        Order order = requireOwnedOrder(customerId, orderNumber);
        MessageThread thread = requireThread(order.getId());
        markCustomerRead(thread);
        return toThreadView(thread, order.getOrderNumber());
    }

    @Transactional
    public ThreadView postCustomer(long customerId, String orderNumber, String body) {
        Order order = requireOwnedOrder(customerId, orderNumber);
        MessageThread thread = requireThread(order.getId());
        return postCustomerMessage(thread, order, body, customerId, order.getContactName());
    }

    @Transactional
    public void markCustomerRead(long customerId, String orderNumber) {
        Order order = requireOwnedOrder(customerId, orderNumber);
        MessageThread thread = requireThread(order.getId());
        markCustomerRead(thread);
    }

    @Transactional
    public TokenResult issueGuestToken(String orderNumber, String email) {
        Order order = requireGuestOrder(orderNumber, email);
        MessageThread thread = requireThread(order.getId());
        markCustomerRead(thread);
        String token = tokens.issue(order.getId());
        return new TokenResult(token, toThreadView(thread, order.getOrderNumber()));
    }

    @Transactional
    public ThreadView forGuestToken(String token) {
        OrderMessageTokenService.Verified v = tokens.verify(token);
        Order order = loadOrder(v.orderId());
        MessageThread thread = requireThread(order.getId());
        markCustomerRead(thread);
        return toThreadView(thread, order.getOrderNumber());
    }

    @Transactional
    public ThreadView postGuest(String token, String body) {
        OrderMessageTokenService.Verified v = tokens.verify(token);
        Order order = loadOrder(v.orderId());
        MessageThread thread = requireThread(order.getId());
        return postCustomerMessage(thread, order, body, null, order.getContactName());
    }

    @Transactional
    public void markGuestRead(String token) {
        OrderMessageTokenService.Verified v = tokens.verify(token);
        MessageThread thread = requireThread(v.orderId());
        markCustomerRead(thread);
    }

    @Transactional(readOnly = true)
    public Page<ThreadSummary> adminList(String status, boolean unreadOnly, String q, Pageable pageable) {
        String st = status == null || status.isBlank() ? null : status.toUpperCase();
        return threads.adminSearch(st, unreadOnly, q, pageable)
            .map(t -> {
                Order order = loadOrder(t.getOrderId());
                return toSummary(t, order.getOrderNumber());
            });
    }

    @Transactional
    public AdminThreadView adminDetail(long threadId) {
        MessageThread thread = loadThread(threadId);
        Order order = loadOrder(thread.getOrderId());
        markAdminRead(thread);
        return toAdminView(thread, order);
    }

    @Transactional
    public AdminThreadView adminReply(long threadId, long adminUserId, String body) {
        MessageThread thread = loadThread(threadId);
        ensureOpen(thread);
        Order order = loadOrder(thread.getOrderId());
        String text = TextSanitizer.plain(body, 4000, "MESSAGE");
        messages.save(new OrderMessage(thread.getId(), AuthorRole.ADMIN, adminUserId, "Scan Lanka", text));
        thread.recordMessage(AuthorRole.ADMIN);
        threads.save(thread);
        audit.log(adminUserId, "ORDER_MESSAGE", "message_thread", String.valueOf(thread.getId()),
            null, "ADMIN_REPLY");
        notifyCustomer(order);
        return toAdminView(thread, order);
    }

    @Transactional
    public AdminThreadView adminClose(long threadId, long adminUserId) {
        MessageThread thread = loadThread(threadId);
        thread.close();
        threads.save(thread);
        audit.log(adminUserId, "ORDER_THREAD_CLOSE", "message_thread", String.valueOf(thread.getId()),
            "OPEN", "CLOSED");
        return toAdminView(thread, loadOrder(thread.getOrderId()));
    }

    @Transactional
    public AdminThreadView adminReopen(long threadId, long adminUserId) {
        MessageThread thread = loadThread(threadId);
        thread.reopen();
        threads.save(thread);
        audit.log(adminUserId, "ORDER_THREAD_REOPEN", "message_thread", String.valueOf(thread.getId()),
            "CLOSED", "OPEN");
        return toAdminView(thread, loadOrder(thread.getOrderId()));
    }

    private ThreadView postCustomerMessage(MessageThread thread, Order order, String body,
                                           Long customerId, String label) {
        ensureOpen(thread);
        String text = TextSanitizer.plain(body, 4000, "MESSAGE");
        String authorLabel = TextSanitizer.optional(label, 160);
        messages.save(new OrderMessage(thread.getId(), AuthorRole.CUSTOMER, customerId, authorLabel, text));
        thread.recordMessage(AuthorRole.CUSTOMER);
        threads.save(thread);
        notifyAdmin(order);
        return toThreadView(thread, order.getOrderNumber());
    }

    private void notifyAdmin(Order order) {
        String link = frontendBase + "/admin/messages/" + threadIdForOrder(order.getId());
        notifications.enqueue("ORDER_MESSAGE", adminEmail,
            HtmlEscaper.subject("New message on order " + order.getOrderNumber()),
            "<p>A customer posted on order <strong>" + HtmlEscaper.escape(order.getOrderNumber())
                + "</strong>.</p><p><a href=\"" + HtmlEscaper.escape(link) + "\">Open thread</a></p>",
            "order-msg-admin:" + order.getId() + ":" + Instant.now().getEpochSecond());
    }

    private void notifyCustomer(Order order) {
        String link = frontendBase + "/orders/lookup";
        notifications.enqueue("ORDER_MESSAGE_REPLY", order.getContactEmail(),
            HtmlEscaper.subject("Update on your Scan Lanka order " + order.getOrderNumber()),
            "<p>We replied to your order <strong>" + HtmlEscaper.escape(order.getOrderNumber())
                + "</strong>.</p><p><a href=\"" + HtmlEscaper.escape(link) + "\">View your order</a></p>",
            "order-msg-reply:" + order.getId() + ":" + Instant.now().getEpochSecond());
    }

    private long threadIdForOrder(long orderId) {
        return threads.findByOrderId(orderId).map(MessageThread::getId).orElse(0L);
    }

    private MessageThread requireThread(long orderId) {
        return threads.findByOrderId(orderId)
            .orElseGet(() -> threads.save(MessageThread.openFor(orderId)));
    }

    private MessageThread loadThread(long id) {
        return threads.findById(id).orElseThrow(OrderMessageService::notFound);
    }

    private Order loadOrder(long orderId) {
        return orders.findById(orderId).orElseThrow(OrderMessageService::notFound);
    }

    private Order requireOwnedOrder(long customerId, String orderNumber) {
        return orders.findByOrderNumber(orderNumber)
            .filter(o -> o.getCustomerId() != null && o.getCustomerId().equals(customerId))
            .orElseThrow(OrderMessageService::notFound);
    }

    private Order requireGuestOrder(String orderNumber, String email) {
        if (!orderNumbers.verify(orderNumber)) throw notFound();
        return orders.findByOrderNumber(orderNumber)
            .filter(o -> o.getContactEmail() != null && o.getContactEmail().equalsIgnoreCase(email))
            .orElseThrow(OrderMessageService::notFound);
    }

    private void markCustomerRead(MessageThread thread) {
        if (thread.getCustomerUnreadCount() > 0) {
            thread.markCustomerRead();
            threads.save(thread);
        }
    }

    private void markAdminRead(MessageThread thread) {
        if (thread.getAdminUnreadCount() > 0) {
            thread.markAdminRead();
            threads.save(thread);
        }
    }

    private static void ensureOpen(MessageThread thread) {
        if (!thread.isOpen()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "CLOSED");
        }
    }

    private ThreadView toThreadView(MessageThread thread, String orderNumber) {
        List<MessageView> msgs = messages.findByThreadIdOrderByCreatedAtAsc(thread.getId()).stream()
            .map(this::toMsg).toList();
        return new ThreadView(thread.getId(), orderNumber, thread.getStatus(),
            thread.getCustomerUnreadCount(), msgs);
    }

    private AdminThreadView toAdminView(MessageThread thread, Order order) {
        List<MessageView> msgs = messages.findByThreadIdOrderByCreatedAtAsc(thread.getId()).stream()
            .map(this::toMsg).toList();
        List<OrderLineView> lines = orderItems.findByOrderId(order.getId()).stream()
            .map(i -> new OrderLineView(i.getNameSnapshot(), i.getSkuSnapshot(),
                i.getQuantity(), i.getLineTotalCents())).toList();
        LinkedOrderView linked = new LinkedOrderView(order.getOrderNumber(), order.getStatus().name(),
            order.getContactName(), order.getContactEmail(), lines);
        return new AdminThreadView(thread.getId(), order.getOrderNumber(), thread.getStatus(),
            thread.getAdminUnreadCount(), msgs, linked);
    }

    private ThreadSummary toSummary(MessageThread thread, String orderNumber) {
        List<OrderMessage> msgs = messages.findByThreadIdOrderByCreatedAtAsc(thread.getId());
        String preview = msgs.isEmpty() ? "" : msgs.get(msgs.size() - 1).getBody();
        if (preview.length() > 120) preview = preview.substring(0, 117) + "...";
        return new ThreadSummary(thread.getId(), orderNumber, thread.getStatus(),
            thread.getAdminUnreadCount(), preview,
            thread.getLastMessageAt() != null ? thread.getLastMessageAt() : thread.getUpdatedAt());
    }

    private MessageView toMsg(OrderMessage m) {
        String role = m.getAuthorRole().name();
        String label = m.getAuthorLabel() != null ? m.getAuthorLabel()
            : (m.getAuthorRole() == AuthorRole.ADMIN ? "Scan Lanka" : "Customer");
        return new MessageView(m.getId(), role, label, m.getBody(), m.getCreatedAt());
    }

    private static ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Not found");
    }
}
