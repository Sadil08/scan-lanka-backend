package com.scanlanka.order.domain;

/** Order lifecycle (09-orders §3) spanning all three payment methods. */
public enum OrderStatus {
    PENDING_PAYMENT,
    AWAITING_BANK_CONFIRMATION,
    PAID,                 // card verified or bank slip confirmed
    CONFIRMED,            // delivery-COD: product paid online, delivery on delivery
    BANK_SLIP_REJECTED,
    PAYMENT_FAILED,
    PACKED,
    SHIPPED,
    READY_FOR_PICKUP,
    DELIVERY_FAILED,
    COMPLETED,            // terminal
    CANCELLED             // terminal
}
