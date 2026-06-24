package com.scanlanka.admin.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/** Audit trail of admin mutations (SEC-ADMIN-2). */
@Entity
@Table(name = "admin_audit_log")
public class AdminAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "admin_id")
    private Long adminId;
    @Column(nullable = false)
    private String action;
    private String entity;
    @Column(name = "entity_id")
    private String entityId;
    @Column(columnDefinition = "text")
    private String before;
    @Column(name = "after", columnDefinition = "text")
    private String after;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected AdminAuditLog() {}

    public AdminAuditLog(Long adminId, String action, String entity, String entityId, String before, String after) {
        this.adminId = adminId;
        this.action = action;
        this.entity = entity;
        this.entityId = entityId;
        this.before = before;
        this.after = after;
    }

    public Long getId() { return id; }
}
