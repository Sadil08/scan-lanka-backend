package com.scanlanka.message.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

@Entity
@Table(name = "message_thread")
public class MessageThread {

    public enum Status { OPEN, CLOSED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false, unique = true)
    private Long orderId;

    @Column(nullable = false)
    private String status = Status.OPEN.name();

    @Column(name = "customer_unread_count", nullable = false)
    private int customerUnreadCount;

    @Column(name = "admin_unread_count", nullable = false)
    private int adminUnreadCount;

    @Column(name = "last_message_at")
    private Instant lastMessageAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected MessageThread() {}

    public static MessageThread openFor(long orderId) {
        MessageThread t = new MessageThread();
        t.orderId = orderId;
        return t;
    }

    public Long getId() { return id; }
    public Long getOrderId() { return orderId; }
    public String getStatus() { return status; }
    public int getCustomerUnreadCount() { return customerUnreadCount; }
    public int getAdminUnreadCount() { return adminUnreadCount; }
    public Instant getLastMessageAt() { return lastMessageAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public boolean isOpen() { return Status.OPEN.name().equals(status); }

    public void close() { this.status = Status.CLOSED.name(); }

    public void reopen() { this.status = Status.OPEN.name(); }

    public void recordMessage(OrderMessage.AuthorRole role) {
        this.lastMessageAt = Instant.now();
        this.updatedAt = Instant.now();
        if (role == OrderMessage.AuthorRole.CUSTOMER) {
            this.adminUnreadCount++;
        } else {
            this.customerUnreadCount++;
        }
    }

    public void markCustomerRead() { this.customerUnreadCount = 0; }

    public void markAdminRead() { this.adminUnreadCount = 0; }
}
