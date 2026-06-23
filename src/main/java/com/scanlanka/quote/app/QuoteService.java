package com.scanlanka.quote.app;

import com.scanlanka.admin.app.AuditService;
import com.scanlanka.catalog.app.ProductLookupService;
import com.scanlanka.catalog.app.ProductLookupService.OrderLine;
import com.scanlanka.notification.app.NotificationService;
import com.scanlanka.order.app.OrderCommands;
import com.scanlanka.order.app.OrderCommands.LineSnapshot;
import com.scanlanka.order.app.OrderService;
import com.scanlanka.order.domain.DeliveryPayment;
import com.scanlanka.order.domain.FulfilmentType;
import com.scanlanka.order.domain.Order;
import com.scanlanka.quote.domain.QuoteItem;
import com.scanlanka.quote.domain.QuoteMessage;
import com.scanlanka.quote.domain.QuoteMessage.Sender;
import com.scanlanka.quote.domain.QuoteRequest;
import com.scanlanka.quote.domain.QuoteRequest.Status;
import com.scanlanka.quote.infra.QuoteItemRepository;
import com.scanlanka.quote.infra.QuoteMessageRepository;
import com.scanlanka.quote.infra.QuoteRequestRepository;
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
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

@Service
public class QuoteService {

    private final QuoteRequestRepository quotes;
    private final QuoteItemRepository items;
    private final QuoteMessageRepository messages;
    private final QuoteTokenService tokens;
    private final ProductLookupService catalog;
    private final OrderService orderService;
    private final NotificationService notifications;
    private final AuditService audit;
    private final String adminEmail;
    private final String frontendBase;

    public QuoteService(QuoteRequestRepository quotes, QuoteItemRepository items,
                        QuoteMessageRepository messages, QuoteTokenService tokens,
                        ProductLookupService catalog, OrderService orderService,
                        NotificationService notifications, AuditService audit,
                        @Value("${app.notifications.admin-email}") String adminEmail,
                        @Value("${app.frontend-base-url}") String frontendBase) {
        this.quotes = quotes;
        this.items = items;
        this.messages = messages;
        this.tokens = tokens;
        this.catalog = catalog;
        this.orderService = orderService;
        this.notifications = notifications;
        this.audit = audit;
        this.adminEmail = adminEmail;
        this.frontendBase = frontendBase;
    }

    public record ItemInput(Long productId, Long variantId, int quantity, String note) {}
    public record SubmitInput(String requesterName, String email, String phone, String country,
                              String message, List<ItemInput> items, Long customerId) {}
    public record SubmitResult(long id, String accessToken) {}
    public record ItemView(long id, String name, int quantity, String note) {}
    public record MessageView(long id, String sender, String body, Long quotedPriceCents, Instant at) {}
    public record QuoteView(long id, String requesterName, String email, String phone, String country,
                            String status, Long quotedTotalCents, Instant expiresAt,
                            List<ItemView> items, List<MessageView> thread) {}

    @Transactional
    public SubmitResult submit(SubmitInput in) {
        if (in.items() == null || in.items().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "EMPTY_ITEMS");
        }
        String name = TextSanitizer.plain(in.requesterName(), 160, "NAME");
        String email = TextSanitizer.email(in.email());
        String phone = TextSanitizer.plain(in.phone(), 32, "PHONE");
        String country = TextSanitizer.optional(in.country(), 2);
        String message = TextSanitizer.optional(in.message(), 4000);
        String token = tokens.issue();
        Instant expires = Instant.now().plus(14, ChronoUnit.DAYS);
        QuoteRequest q = quotes.save(new QuoteRequest(name, email, phone, country, message,
            tokens.hash(token), in.customerId(), expires));
        for (ItemInput line : in.items()) {
            if (line.quantity() < 1) throw badRequest("INVALID_QTY");
            String itemName = resolveName(line);
            items.save(new QuoteItem(q.getId(), line.productId(), line.variantId(),
                itemName, line.quantity(), TextSanitizer.optional(line.note(), 300)));
        }
        String link = frontendBase + "/quotes/" + token;
        notifications.enqueue("QUOTE_REQUEST", adminEmail,
            HtmlEscaper.subject("New quote request from " + name),
            "<p>" + HtmlEscaper.escape(name) + " — " + HtmlEscaper.escape(email) + "</p>"
                + "<p><a href=\"" + HtmlEscaper.escape(link) + "\">View thread</a></p>",
            "quote:" + q.getId());
        return new SubmitResult(q.getId(), token);
    }

    @Transactional(readOnly = true)
    public QuoteView forToken(String token) {
        QuoteRequest q = byToken(token);
        return toView(q);
    }

    @Transactional
    public MessageView requesterMessage(String token, String body) {
        QuoteRequest q = byToken(token);
        ensureOpen(q);
        String text = TextSanitizer.plain(body, 4000, "BODY");
        QuoteMessage msg = messages.save(new QuoteMessage(q.getId(), Sender.REQUESTER, text, null));
        if (q.getStatus().equals(Status.NEW.name())) {
            q.setStatus(Status.NEGOTIATING);
            quotes.save(q);
        }
        return toMsg(msg);
    }

    @Transactional(readOnly = true)
    public Page<QuoteView> adminList(String status, Pageable pageable) {
        Page<QuoteRequest> page = (status == null || status.isBlank())
            ? quotes.findAllByOrderByCreatedAtDesc(pageable)
            : quotes.findByStatusOrderByCreatedAtDesc(status.toUpperCase(), pageable);
        return page.map(this::toView);
    }

    @Transactional(readOnly = true)
    public QuoteView adminDetail(long id) {
        return toView(load(id));
    }

    @Transactional
    public MessageView adminMessage(long id, Long adminId, String body, Long quotedPriceCents) {
        QuoteRequest q = load(id);
        String text = TextSanitizer.plain(body, 4000, "BODY");
        QuoteMessage msg = messages.save(new QuoteMessage(q.getId(), Sender.ADMIN, text, quotedPriceCents));
        if (quotedPriceCents != null && quotedPriceCents > 0) {
            q.setQuotedTotalCents(quotedPriceCents);
            q.setStatus(Status.QUOTED);
        } else if (!q.getStatus().equals(Status.ACCEPTED.name())) {
            q.setStatus(Status.NEGOTIATING);
        }
        quotes.save(q);
        audit.log(adminId, "QUOTE_MESSAGE", "quote_request", String.valueOf(id), null, q.getStatus());
        notifications.enqueue("QUOTE_REPLY", q.getEmail(),
            HtmlEscaper.subject("Update on your Scan Lanka quote"),
            "<p>" + HtmlEscaper.escape(text) + "</p>",
            "quote-reply:" + msg.getId());
        return toMsg(msg);
    }

    @Transactional
    public void adminAccept(long id, Long adminId) {
        QuoteRequest q = load(id);
        if (q.getQuotedTotalCents() == null) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "NO_PRICE");
        }
        q.setStatus(Status.ACCEPTED);
        quotes.save(q);
        audit.log(adminId, "QUOTE_ACCEPT", "quote_request", String.valueOf(id), null, "ACCEPTED");
    }

    @Transactional
    public void requesterAccept(String token) {
        QuoteRequest q = byToken(token);
        if (q.getQuotedTotalCents() == null) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "NO_PRICE");
        }
        q.setStatus(Status.ACCEPTED);
        quotes.save(q);
    }

    @Transactional
    public String convertToOrder(long id, Long adminId) {
        QuoteRequest q = load(id);
        if (!Status.ACCEPTED.name().equals(q.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "NOT_ACCEPTED");
        }
        if (q.isExpired()) {
            q.setStatus(Status.EXPIRED);
            quotes.save(q);
            throw new ResponseStatusException(HttpStatus.CONFLICT, "EXPIRED");
        }
        if (q.getConvertedOrderId() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "ALREADY_CONVERTED");
        }
        List<QuoteItem> lines = items.findByQuoteId(q.getId());
        long total = q.getQuotedTotalCents();
        int totalQty = lines.stream().mapToInt(QuoteItem::getQuantity).sum();
        List<LineSnapshot> snapshots = new ArrayList<>();
        long allocated = 0;
        for (int i = 0; i < lines.size(); i++) {
            QuoteItem li = lines.get(i);
            long lineTotal = (i == lines.size() - 1)
                ? total - allocated
                : total * li.getQuantity() / totalQty;
            allocated += lineTotal;
            long unit = lineTotal / li.getQuantity();
            OrderLine ol = catalog.resolveOrderLine(li.getProductId(), li.getVariantId()).orElse(null);
            String sku = ol != null ? ol.sku() : "QUOTE";
            String handling = ol != null ? ol.handlingClass() : null;
            snapshots.add(new LineSnapshot(li.getProductId(), li.getVariantId(), sku,
                li.getNameSnapshot(), handling, unit, li.getQuantity(), lineTotal));
        }
        OrderCommands.CreateOrderCommand cmd = new OrderCommands.CreateOrderCommand(
            q.getCustomerId(), q.getCustomerId() == null ? q.getEmail() : null,
            q.getRequesterName(), q.getPhone(), q.getEmail(),
            FulfilmentType.PICKUP_SHOP, null, null, snapshots,
            total, 0, 0, 0, total, DeliveryPayment.PREPAID, 0);
        Order order = orderService.createDraft(cmd);
        q.setConvertedOrderId(order.getId());
        quotes.save(q);
        audit.log(adminId, "QUOTE_CONVERT", "quote_request", String.valueOf(id), null, order.getOrderNumber());
        return order.getOrderNumber();
    }

    private String resolveName(ItemInput line) {
        if (line.productId() != null) {
            return catalog.resolveOrderLine(line.productId(), line.variantId())
                .map(OrderLine::name)
                .orElse("Product " + line.productId());
        }
        throw badRequest("INVALID_ITEM");
    }

    private QuoteRequest byToken(String token) {
        String hash = tokens.hash(token);
        return quotes.findByAccessTokenHash(hash)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Not found"));
    }

    private QuoteRequest load(long id) {
        return quotes.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Not found"));
    }

    private void ensureOpen(QuoteRequest q) {
        if (q.isExpired() || Status.REJECTED.name().equals(q.getStatus())
            || Status.EXPIRED.name().equals(q.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "CLOSED");
        }
    }

    private QuoteView toView(QuoteRequest q) {
        List<ItemView> itemViews = items.findByQuoteId(q.getId()).stream()
            .map(i -> new ItemView(i.getId(), i.getNameSnapshot(), i.getQuantity(), i.getNote()))
            .toList();
        List<MessageView> thread = messages.findByQuoteIdOrderByCreatedAtAsc(q.getId()).stream()
            .map(this::toMsg).toList();
        return new QuoteView(q.getId(), q.getRequesterName(), q.getEmail(), q.getPhone(), q.getCountry(),
            q.getStatus(), q.getQuotedTotalCents(), q.getExpiresAt(), itemViews, thread);
    }

    private MessageView toMsg(QuoteMessage m) {
        return new MessageView(m.getId(), m.getSender(), m.getBody(), m.getQuotedPriceCents(), m.getCreatedAt());
    }

    private static ResponseStatusException badRequest(String code) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, code);
    }
}
