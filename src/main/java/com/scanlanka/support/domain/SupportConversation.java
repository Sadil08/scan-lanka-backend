package com.scanlanka.support.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

@Entity
@Table(name = "support_conversation")
public class SupportConversation {

    public enum Status { OPEN, CLOSED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "visitor_name")
    private String visitorName;
    @Column(name = "visitor_email")
    private String visitorEmail;
    @Column(name = "access_token_hash", nullable = false, unique = true)
    private String accessTokenHash;
    @Column(name = "customer_id")
    private Long customerId;
    @Column(nullable = false)
    private String status = Status.OPEN.name();
    @Column(name = "page_context")
    private String pageContext;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected SupportConversation() {}

    public SupportConversation(String accessTokenHash, String visitorName, String visitorEmail,
                             Long customerId, String pageContext) {
        this.accessTokenHash = accessTokenHash;
        this.visitorName = visitorName;
        this.visitorEmail = visitorEmail;
        this.customerId = customerId;
        this.pageContext = pageContext;
    }

    public Long getId() { return id; }
    public String getVisitorName() { return visitorName; }
    public String getVisitorEmail() { return visitorEmail; }
    public String getAccessTokenHash() { return accessTokenHash; }
    public Long getCustomerId() { return customerId; }
    public String getStatus() { return status; }
    public String getPageContext() { return pageContext; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public boolean isOpen() { return Status.OPEN.name().equals(status); }

    public void close() { this.status = Status.CLOSED.name(); }

    public void touch() { this.updatedAt = Instant.now(); }
}
