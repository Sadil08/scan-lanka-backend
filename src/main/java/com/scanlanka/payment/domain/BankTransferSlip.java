package com.scanlanka.payment.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/** An uploaded bank-transfer slip awaiting manual admin confirmation (06 FR-PAY-6, T-1b). */
@Entity
@Table(name = "bank_transfer_slip")
public class BankTransferSlip {

    public enum Review { PENDING, CONFIRMED, REJECTED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "payment_id", nullable = false)
    private Long paymentId;
    @Column(name = "storage_key", nullable = false)
    private String storageKey;
    @Column(nullable = false)
    private String url;
    @Column(name = "slip_hash", nullable = false)
    private String slipHash;
    @Column(name = "review_status", nullable = false)
    private String reviewStatus = Review.PENDING.name();
    @Column(name = "review_note")
    private String reviewNote;
    @Column(name = "reviewed_by")
    private Long reviewedBy;

    @CreationTimestamp
    @Column(name = "uploaded_at", nullable = false, updatable = false)
    private Instant uploadedAt;

    protected BankTransferSlip() {}

    public BankTransferSlip(Long paymentId, String storageKey, String url, String slipHash) {
        this.paymentId = paymentId;
        this.storageKey = storageKey;
        this.url = url;
        this.slipHash = slipHash;
    }

    public Long getId() { return id; }
    public void review(Review status, Long adminId, String note) {
        this.reviewStatus = status.name();
        this.reviewedBy = adminId;
        this.reviewNote = note;
    }
}
