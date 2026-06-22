package com.scanlanka.wishlist.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/** Saved product reference for a customer (03-wishlist). Product-level, not variant. */
@Entity
@Table(name = "wishlist_item")
public class WishlistItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected WishlistItem() {}

    public WishlistItem(Long customerId, Long productId) {
        this.customerId = customerId;
        this.productId = productId;
    }

    public Long getId() { return id; }
    public Long getCustomerId() { return customerId; }
    public Long getProductId() { return productId; }
}
