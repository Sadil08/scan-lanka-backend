package com.scanlanka.order.domain;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

import static com.scanlanka.order.domain.OrderStatus.*;

/**
 * Allowed order-status transitions (09-orders §3, FR-ORDER-3). Illegal transitions are rejected.
 * Pure logic — DB-free, fully testable.
 */
public final class OrderStateMachine {

    private static final Map<OrderStatus, Set<OrderStatus>> ALLOWED = new EnumMap<>(OrderStatus.class);

    static {
        ALLOWED.put(PENDING_PAYMENT, Set.of(PAID, AWAITING_BANK_CONFIRMATION, CONFIRMED, PAYMENT_FAILED, CANCELLED));
        ALLOWED.put(AWAITING_BANK_CONFIRMATION, Set.of(PAID, BANK_SLIP_REJECTED, CANCELLED));
        ALLOWED.put(BANK_SLIP_REJECTED, Set.of(AWAITING_BANK_CONFIRMATION, CANCELLED));
        ALLOWED.put(PAYMENT_FAILED, Set.of(PENDING_PAYMENT, CANCELLED));
        ALLOWED.put(PAID, Set.of(PACKED, CANCELLED));
        ALLOWED.put(CONFIRMED, Set.of(PACKED, CANCELLED));
        ALLOWED.put(PACKED, Set.of(SHIPPED, READY_FOR_PICKUP, CANCELLED));
        ALLOWED.put(SHIPPED, Set.of(COMPLETED, DELIVERY_FAILED));
        ALLOWED.put(READY_FOR_PICKUP, Set.of(COMPLETED, CANCELLED));
        ALLOWED.put(DELIVERY_FAILED, Set.of(SHIPPED, CANCELLED));
        ALLOWED.put(COMPLETED, Set.of());   // terminal
        ALLOWED.put(CANCELLED, Set.of());   // terminal
    }

    private OrderStateMachine() {}

    public static boolean canTransition(OrderStatus from, OrderStatus to) {
        return ALLOWED.getOrDefault(from, Set.of()).contains(to);
    }

    public static boolean isTerminal(OrderStatus status) {
        return ALLOWED.getOrDefault(status, Set.of()).isEmpty();
    }
}
