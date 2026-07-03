package com.scanlanka.inbox.app;

import com.scanlanka.auth.domain.Role;
import com.scanlanka.auth.infra.AppUserRepository;
import com.scanlanka.inbox.domain.CustomerInboxItem;
import com.scanlanka.inbox.domain.CustomerInboxItem.Type;
import com.scanlanka.inbox.infra.CustomerInboxRepository;
import com.scanlanka.shared.text.TextSanitizer;
import com.scanlanka.wishlist.infra.WishlistItemRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;

@Service
public class CustomerInboxService {

    private final CustomerInboxRepository repo;
    private final WishlistItemRepository wishlist;
    private final AppUserRepository users;

    public CustomerInboxService(CustomerInboxRepository repo, WishlistItemRepository wishlist,
                                AppUserRepository users) {
        this.repo = repo;
        this.wishlist = wishlist;
        this.users = users;
    }

    public record InboxView(long id, String type, String title, String body, String link,
                            boolean read, Instant at) {}
    public record InboxPage(List<InboxView> items, long unreadCount, int page, int totalPages) {}
    public record Summary(long unreadCount) {}

    @Transactional(readOnly = true)
    public InboxPage list(long customerId, int page) {
        Page<CustomerInboxItem> p = repo.findByCustomerIdOrderByCreatedAtDesc(
            customerId, PageRequest.of(Math.max(0, page), 25));
        long unread = repo.countByCustomerIdAndReadAtIsNull(customerId);
        List<InboxView> items = p.getContent().stream().map(this::toView).toList();
        return new InboxPage(items, unread, p.getNumber(), p.getTotalPages());
    }

    @Transactional(readOnly = true)
    public Summary summary(long customerId) {
        return new Summary(repo.countByCustomerIdAndReadAtIsNull(customerId));
    }

    @Transactional
    public void markRead(long customerId, long id) {
        CustomerInboxItem item = repo.findById(id)
            .filter(i -> i.getCustomerId().equals(customerId))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Not found"));
        if (item.getReadAt() == null) {
            item.markRead();
            repo.save(item);
        }
    }

    @Transactional
    public void markAllRead(long customerId) {
        repo.markAllRead(customerId, Instant.now());
    }

    @Transactional
    public void notifyOrderReply(long customerId, String orderNumber) {
        push(customerId, Type.ORDER_MESSAGE,
            "Reply on order " + orderNumber,
            "Scan Lanka replied to your order message.",
            "/account/orders/" + orderNumber,
            "order-msg:" + orderNumber + ":" + Instant.now().getEpochSecond());
    }

    @Transactional
    public void notifyQuoteReply(long customerId, long quoteId, String preview) {
        String body = preview != null && preview.length() > 180 ? preview.substring(0, 177) + "..." : preview;
        push(customerId, Type.QUOTE_REPLY,
            "Update on your quote request",
            body != null ? body : "We sent you a message about your quote.",
            "/account",
            "quote-reply:" + quoteId + ":" + Instant.now().getEpochSecond());
    }

    @Transactional
    public void notifyStockRestock(long productId, String productName, String slug) {
        String title = productName + " is back in stock";
        List<Long> customerIds = wishlist.findCustomerIdsByProductId(productId);
        for (Long customerId : customerIds) {
            push(customerId, Type.STOCK_RESTOCK, title,
                "An item on your wishlist is available again.",
                "/products/" + slug,
                "restock:" + productId + ":" + customerId);
        }
    }

    @Transactional
    public void notifyNewProduct(long productId, String productName, String slug) {
        String title = "New product: " + productName;
        List<Long> customerIds = users.findActiveCustomerIds(Role.CUSTOMER);
        for (Long customerId : customerIds) {
            push(customerId, Type.NEW_PRODUCT, title,
                "A new item was added to our catalog.",
                "/products/" + slug,
                "new-product:" + productId + ":" + customerId);
        }
    }

    private void push(Long customerId, Type type, String title, String body, String link, String sourceKey) {
        if (customerId == null) return;
        if (sourceKey != null && repo.existsByCustomerIdAndSourceKey(customerId, sourceKey)) return;
        String safeTitle = TextSanitizer.optional(title, 200);
        String safeBody = TextSanitizer.optional(body, 500);
        String safeLink = TextSanitizer.optional(link, 500);
        if (safeTitle == null) return;
        repo.save(new CustomerInboxItem(customerId, type, safeTitle, safeBody, safeLink, sourceKey));
    }

    private InboxView toView(CustomerInboxItem i) {
        return new InboxView(i.getId(), i.getType(), i.getTitle(), i.getBody(), i.getLink(),
            i.getReadAt() != null, i.getCreatedAt());
    }
}
