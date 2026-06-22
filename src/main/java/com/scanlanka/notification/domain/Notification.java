package com.scanlanka.notification.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/** An outbox email row (10). Rendered at enqueue; sent asynchronously by the worker; idempotent. */
@Entity
@Table(name = "notification")
public class Notification {

    public enum Status { PENDING, SENT, RETRYING, FAILED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String type;
    @Column(nullable = false)
    private String recipient;
    @Column(nullable = false)
    private String subject;
    @Column(nullable = false, columnDefinition = "text")
    private String body;
    @Column(name = "idempotency_key", nullable = false, unique = true)
    private String idempotencyKey;
    @Column(nullable = false)
    private String status = Status.PENDING.name();
    @Column(nullable = false)
    private int attempts;
    @Column(name = "next_attempt_at")
    private Instant nextAttemptAt = Instant.now();
    @Column(name = "last_error")
    private String lastError;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "sent_at")
    private Instant sentAt;

    protected Notification() {}

    public Notification(String type, String recipient, String subject, String body, String idempotencyKey) {
        this.type = type;
        this.recipient = recipient;
        this.subject = subject;
        this.body = body;
        this.idempotencyKey = idempotencyKey;
    }

    public Long getId() { return id; }
    public String getType() { return type; }
    public String getRecipient() { return recipient; }
    public String getSubject() { return subject; }
    public String getBody() { return body; }
    public String getStatus() { return status; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public String getLastError() { return lastError; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getSentAt() { return sentAt; }

    public void resetForRetry() {
        this.status = Status.PENDING.name();
        this.attempts = 0;
        this.lastError = null;
        this.nextAttemptAt = Instant.now();
        this.sentAt = null;
    }

    public int getAttempts() { return attempts; }

    public void markSent() {
        this.status = Status.SENT.name();
        this.sentAt = Instant.now();
    }

    public void retryLater(Instant when) {
        this.attempts++;
        this.status = Status.RETRYING.name();
        this.nextAttemptAt = when;
    }

    public void fail(String error) {
        this.attempts++;
        this.status = Status.FAILED.name();
        this.lastError = truncate(error);
    }

    private static String truncate(String s) {
        return s == null ? null : (s.length() > 480 ? s.substring(0, 480) : s);
    }
}
