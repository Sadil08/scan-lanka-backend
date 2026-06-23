package com.scanlanka.returns.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/** Record of an offline-processed refund (16 FR-RETURN-3). Order row is never mutated. */
@Entity
@Table(name = "refund")
public class Refund {

    public enum Method { PAYHERE, BANK, STORE_CREDIT }
    public enum Status { RECORDED, DONE, FAILED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false)
    private Long orderId;
    @Column(name = "amount_cents", nullable = false)
    private long amountCents;
    @Column(nullable = false)
    private String method;
    private String reason;
    @Column(name = "gateway_ref")
    private String gatewayRef;
    @Column(nullable = false)
    private String status = Status.RECORDED.name();
    @Column(name = "idempotency_key", nullable = false, unique = true)
    private String idempotencyKey;
    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Refund() {}

    public Refund(Long orderId, long amountCents, Method method, String reason, String gatewayRef,
                  String idempotencyKey, Long createdBy) {
        this.orderId = orderId;
        this.amountCents = amountCents;
        this.method = method.name();
        this.reason = reason;
        this.gatewayRef = gatewayRef;
        this.idempotencyKey = idempotencyKey;
        this.createdBy = createdBy;
    }

    public Long getId() { return id; }
    public Long getOrderId() { return orderId; }
    public long getAmountCents() { return amountCents; }
    public String getMethod() { return method; }
    public String getReason() { return reason; }
    public String getGatewayRef() { return gatewayRef; }
    public String getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
}
