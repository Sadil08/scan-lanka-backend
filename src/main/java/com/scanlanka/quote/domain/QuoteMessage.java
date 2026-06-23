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
@Table(name = "quote_message")
public class QuoteMessage {

    public enum Sender { ADMIN, REQUESTER }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "quote_id", nullable = false)
    private Long quoteId;
    @Column(nullable = false)
    private String sender;
    @Column(nullable = false)
    private String body;
    @Column(name = "quoted_price_cents")
    private Long quotedPriceCents;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected QuoteMessage() {}

    public QuoteMessage(Long quoteId, Sender sender, String body, Long quotedPriceCents) {
        this.quoteId = quoteId;
        this.sender = sender.name();
        this.body = body;
        this.quotedPriceCents = quotedPriceCents;
    }

    public Long getId() { return id; }
    public String getSender() { return sender; }
    public String getBody() { return body; }
    public Long getQuotedPriceCents() { return quotedPriceCents; }
    public Instant getCreatedAt() { return createdAt; }
}
