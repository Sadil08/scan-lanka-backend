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
 * Alerts Scan Lanka when a prepaid bank-transfer order is placed (still PENDING_PAYMENT).
 * Runs synchronously inside the placing transaction on {@link OrderPlacedEvent}.
 *
 * <p>Keyed on {@code deliveryPayment != COD} and {@code paymentMethod != CARD}: COD is confirmed
 * at placement and already emails the buyer via {@link OrderNotificationComposer#onOrderConfirmed}.
 * PayHere (CARD) attempts are silent at placement — the buyer and admin are emailed only after
 * payment is confirmed — so abandoned card checkouts do not spam the inbox. Bank-transfer orders
 * still alert admin at placement because a slip is expected.
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
        // PayHere card attempts: wait for notify before emailing anyone.
        if ("CARD".equalsIgnoreCase(order.getPaymentMethod())) {
            return;
        }
        List<OrderItem> items = orderItems.findByOrderId(order.getId());
        composer.onOrderPlaced(order, items);
    }
}
