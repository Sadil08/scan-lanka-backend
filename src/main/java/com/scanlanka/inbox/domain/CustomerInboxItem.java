package com.scanlanka.inbox.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Entity
@Table(name = "customer_inbox")
public class CustomerInboxItem {

    public enum Type {
        ORDER_MESSAGE, QUOTE_REPLY, STOCK_RESTOCK, NEW_PRODUCT
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Column(nullable = false)
    private String type;

    @Column(nullable = false)
    private String title;

    private String body;

    private String link;

    @Column(name = "source_key")
    private String sourceKey;

    @Column(name = "read_at")
    private Instant readAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected CustomerInboxItem() {}

    public CustomerInboxItem(Long customerId, Type type, String title, String body, String link, String sourceKey) {
        this.customerId = customerId;
        this.type = type.name();
        this.title = title;
        this.body = body;
        this.link = link;
        this.sourceKey = sourceKey;
    }

    public Long getId() { return id; }
    public Long getCustomerId() { return customerId; }
    public String getType() { return type; }
    public String getTitle() { return title; }
    public String getBody() { return body; }
    public String getLink() { return link; }
    public String getSourceKey() { return sourceKey; }
    public Instant getReadAt() { return readAt; }
    public Instant getCreatedAt() { return createdAt; }

    public void markRead() { this.readAt = Instant.now(); }
}
