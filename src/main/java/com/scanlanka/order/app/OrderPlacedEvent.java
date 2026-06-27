package com.scanlanka.order.app;

/**
 * Published after an order draft is placed + stock soft-reserved (checkout). Listeners act by loading
 * the order (so the event stays free of checkout/payment types): payment confirms full-COD courier
 * orders immediately (06), and the order message portal (19) will open a thread. Synchronous — handled
 * within the placing transaction.
 */
public record OrderPlacedEvent(Long orderId) {}
