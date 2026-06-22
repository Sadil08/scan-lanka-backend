package com.scanlanka.order.domain;

/** Per-line fulfilment status (08 FR-ADMIN-6d). */
public enum OrderItemStatus {
    PENDING, PREPARING, PREPARED, SHIPPED, DELIVERED, CANCELLED
}
