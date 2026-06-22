package com.scanlanka.payment.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/** Payment record for an order (06). PAID only on verified gateway notify / admin slip-confirm. */
@Entity
@Table(name = "payment")
public class Payment {

    public enum Method { PAYHERE, BANK_TRANSFER }
    public enum Status { PENDING, PAID, FAILED, AWAITING_BANK_CONFIRMATION, BANK_REJECTED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false, unique = true)
    private Long orderId;

    @Column(nullable = false)
    private String method;

    @Column(nullable = false)
    private String status;

    @Column(name = "online_amount_cents", nullable = false)
    private long onlineAmountCents;

    @Column(nullable = false)
    private String currency = "LKR";

    @Column(name = "gateway_payment_id")
    private String gatewayPaymentId;
    @Column(name = "gateway_status_code")
    private String gatewayStatusCode;
    @Column(name = "confirmed_at")
    private Instant confirmedAt;
    @Column(name = "confirmed_by")
    private Long confirmedBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Payment() {}

    public Payment(Long orderId, Method method, Status status, long onlineAmountCents, String currency) {
        this.orderId = orderId;
        this.method = method.name();
        this.status = status.name();
        this.onlineAmountCents = onlineAmountCents;
        this.currency = currency;
    }

    public Long getId() { return id; }
    public Long getOrderId() { return orderId; }
    public String getMethod() { return method; }
    public String getStatus() { return status; }
    public void setStatus(Status status) { this.status = status.name(); }
    public void markPaid(String gatewayPaymentId, String statusCode) {
        this.status = Status.PAID.name();
        this.gatewayPaymentId = gatewayPaymentId;
        this.gatewayStatusCode = statusCode;
        this.confirmedAt = Instant.now();
    }
}
