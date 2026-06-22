package com.scanlanka.order.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/** Stored receipt PDF key for an order (09 FR-18). */
@Entity
@Table(name = "order_receipt")
public class OrderReceipt {

    @Id
    @Column(name = "order_id")
    private Long orderId;

    @Column(name = "storage_key", nullable = false)
    private String storageKey;

    @CreationTimestamp
    @Column(name = "generated_at", nullable = false, updatable = false)
    private Instant generatedAt;

    protected OrderReceipt() {}

    public OrderReceipt(Long orderId, String storageKey) {
        this.orderId = orderId;
        this.storageKey = storageKey;
    }

    public Long getOrderId() { return orderId; }
    public String getStorageKey() { return storageKey; }
}
