package com.scanlanka.support.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Entity
@Table(name = "support_message")
public class SupportMessage {

    public enum Sender { VISITOR, ADMIN }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "conversation_id", nullable = false)
    private Long conversationId;
    @Column(nullable = false)
    private String sender;
    @Column(nullable = false)
    private String body;
    @Column(name = "admin_user_id")
    private Long adminUserId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected SupportMessage() {}

    public SupportMessage(Long conversationId, Sender sender, String body, Long adminUserId) {
        this.conversationId = conversationId;
        this.sender = sender.name();
        this.body = body;
        this.adminUserId = adminUserId;
    }

    public Long getId() { return id; }
    public String getSender() { return sender; }
    public String getBody() { return body; }
    public Instant getCreatedAt() { return createdAt; }
}
