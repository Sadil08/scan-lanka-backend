package com.scanlanka.quote.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Entity
@Table(name = "quote_request")
public class QuoteRequest {

    public enum Status { NEW, QUOTED, NEGOTIATING, ACCEPTED, REJECTED, EXPIRED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "requester_name", nullable = false)
    private String requesterName;
    @Column(nullable = false)
    private String email;
    @Column(nullable = false)
    private String phone;
    private String country;
    private String message;
    @Column(nullable = false)
    private String status = Status.NEW.name();
    @Column(name = "quoted_total_cents")
    private Long quotedTotalCents;
    @Column(name = "access_token_hash", nullable = false)
    private String accessTokenHash;
    @Column(name = "customer_id")
    private Long customerId;
    @Column(name = "expires_at")
    private Instant expiresAt;
    @Column(name = "converted_order_id")
    private Long convertedOrderId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected QuoteRequest() {}

    public QuoteRequest(String requesterName, String email, String phone, String country, String message,
                        String accessTokenHash, Long customerId, Instant expiresAt) {
        this.requesterName = requesterName;
        this.email = email;
        this.phone = phone;
        this.country = country;
        this.message = message;
        this.accessTokenHash = accessTokenHash;
        this.customerId = customerId;
        this.expiresAt = expiresAt;
    }

    public Long getId() { return id; }
    public String getRequesterName() { return requesterName; }
    public String getEmail() { return email; }
    public String getPhone() { return phone; }
    public String getCountry() { return country; }
    public String getMessage() { return message; }
    public String getStatus() { return status; }
    public Long getQuotedTotalCents() { return quotedTotalCents; }
    public String getAccessTokenHash() { return accessTokenHash; }
    public Instant getExpiresAt() { return expiresAt; }
    public Long getConvertedOrderId() { return convertedOrderId; }
    public Instant getCreatedAt() { return createdAt; }

    public void setStatus(Status status) { this.status = status.name(); }
    public void setQuotedTotalCents(Long cents) { this.quotedTotalCents = cents; }
    public void setConvertedOrderId(Long orderId) { this.convertedOrderId = orderId; }

    public boolean isExpired() {
        return expiresAt != null && Instant.now().isAfter(expiresAt);
    }
}
