package com.scanlanka.payment.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/** Gateway callback audit + idempotency key (06 FR-PAY-4, T-4). Unique (provider, ref, status). */
@Entity
@Table(name = "payment_event")
public class PaymentEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String provider;
    @Column(name = "external_ref", nullable = false)
    private String externalRef;
    @Column(name = "status_code")
    private String statusCode;
    @Column(name = "signature_ok", nullable = false)
    private boolean signatureOk;
    @Column(nullable = false)
    private boolean processed;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected PaymentEvent() {}

    public PaymentEvent(String provider, String externalRef, String statusCode, boolean signatureOk) {
        this.provider = provider;
        this.externalRef = externalRef;
        this.statusCode = statusCode;
        this.signatureOk = signatureOk;
    }

    public void markProcessed() { this.processed = true; }
}
