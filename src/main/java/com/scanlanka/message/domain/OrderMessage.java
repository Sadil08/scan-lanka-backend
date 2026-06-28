package com.scanlanka.message.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Entity
@Table(name = "order_message")
public class OrderMessage {

    public enum AuthorRole { CUSTOMER, ADMIN }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "thread_id", nullable = false)
    private Long threadId;

    @Enumerated(EnumType.STRING)
    @Column(name = "author_role", nullable = false)
    private AuthorRole authorRole;

    @Column(name = "author_user_id")
    private Long authorUserId;

    @Column(name = "author_label")
    private String authorLabel;

    @Column(nullable = false)
    private String body;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected OrderMessage() {}

    public OrderMessage(long threadId, AuthorRole authorRole, Long authorUserId, String authorLabel, String body) {
        this.threadId = threadId;
        this.authorRole = authorRole;
        this.authorUserId = authorUserId;
        this.authorLabel = authorLabel;
        this.body = body;
    }

    public Long getId() { return id; }
    public Long getThreadId() { return threadId; }
    public AuthorRole getAuthorRole() { return authorRole; }
    public Long getAuthorUserId() { return authorUserId; }
    public String getAuthorLabel() { return authorLabel; }
    public String getBody() { return body; }
    public Instant getCreatedAt() { return createdAt; }
}
