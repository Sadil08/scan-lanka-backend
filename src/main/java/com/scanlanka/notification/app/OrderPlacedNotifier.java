package com.scanlanka.notification.app;

import com.scanlanka.order.app.OrderPlacedEvent;
import com.scanlanka.order.domain.DeliveryPayment;
import com.scanlanka.order.domain.Order;
import com.scanlanka.order.domain.OrderItem;
import com.scanlanka.order.infra.OrderItemRepository;
import com.scanlanka.order.infra.OrderRepository;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Emails the buyer and Scan Lanka the moment an order is placed (owner 2026-07-23). Runs synchronously
 * inside the placing transaction on {@link OrderPlacedEvent}.
 *
 * <p>Keyed on {@code deliveryPayment != COD}: COD orders are confirmed at placement and already emailed
 * via the confirmation path ({@link OrderNotificationComposer#onOrderConfirmed}), so sending here too
 * would double up. Non-COD orders (lorry-online, bank transfer, PayHere) sit PENDING_PAYMENT and would
 * otherwise get nothing until payment is confirmed — this is the gap we close. Deterministic regardless
 * of listener ordering because it depends only on the order's own payment choice, not its live status.
 */
@Component
public class OrderPlacedNotifier {

    private final OrderRepository orders;
    private final OrderItemRepository orderItems;
    private final OrderNotificationComposer composer;

    public OrderPlacedNotifier(OrderRepository orders, OrderItemRepository orderItems,
                               OrderNotificationComposer composer) {
        this.orders = orders;
        this.orderItems = orderItems;
        this.composer = composer;
    }

    @EventListener
    public void onOrderPlaced(OrderPlacedEvent event) {
        Order order = orders.findById(event.orderId()).orElse(null);
        if (order == null || order.getDeliveryPayment() == DeliveryPayment.COD) {
            return; // COD is covered by the confirmation email fired at placement
        }
        List<OrderItem> items = orderItems.findByOrderId(order.getId());
        composer.onOrderPlaced(order, items);
    }
}
