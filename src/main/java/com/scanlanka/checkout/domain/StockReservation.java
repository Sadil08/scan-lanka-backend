package com.scanlanka.checkout.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** Soft stock hold during checkout/payment (05 FR-CHECKOUT-7). */
@Entity
@Table(name = "stock_reservation")
public class StockReservation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false)
    private Long orderId;
    @Column(name = "product_id", nullable = false)
    private Long productId;
    @Column(name = "variant_id")
    private Long variantId;
    @Column(nullable = false)
    private int quantity;
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;
    @Column(nullable = false)
    private boolean released;

    protected StockReservation() {}

    public StockReservation(Long orderId, Long productId, Long variantId, int quantity, Instant expiresAt) {
        this.orderId = orderId;
        this.productId = productId;
        this.variantId = variantId;
        this.quantity = quantity;
        this.expiresAt = expiresAt;
        this.released = false;
    }

    public Long getId() { return id; }
    public boolean isReleased() { return released; }
    public void setReleased(boolean released) { this.released = released; }
}
