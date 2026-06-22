package com.scanlanka.order.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/** Audit trail of order status changes (09 NFR-ORDER-2). */
@Entity
@Table(name = "order_status_event")
public class OrderStatusEvent {

    public enum ActorType { SYSTEM, ADMIN, CUSTOMER }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "from_status")
    private String fromStatus;
    @Column(name = "to_status", nullable = false)
    private String toStatus;
    @Column(name = "actor_type", nullable = false)
    private String actorType;
    @Column(name = "actor_id")
    private Long actorId;
    private String note;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected OrderStatusEvent() {}

    public OrderStatusEvent(Long orderId, OrderStatus from, OrderStatus to, ActorType actorType, Long actorId, String note) {
        this.orderId = orderId;
        this.fromStatus = from == null ? null : from.name();
        this.toStatus = to.name();
        this.actorType = actorType.name();
        this.actorId = actorId;
        this.note = note;
    }

    public Long getId() { return id; }
    public String getFromStatus() { return fromStatus; }
    public String getToStatus() { return toStatus; }
    public String getNote() { return note; }
    public Instant getCreatedAt() { return createdAt; }
}
