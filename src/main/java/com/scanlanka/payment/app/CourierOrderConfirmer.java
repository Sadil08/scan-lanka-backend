package com.scanlanka.payment.app;

import com.scanlanka.order.app.OrderPlacedEvent;
import com.scanlanka.order.domain.Order;
import com.scanlanka.order.domain.OrderStatus;
import com.scanlanka.order.domain.OrderStatusEvent.ActorType;
import com.scanlanka.order.infra.OrderRepository;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Courier orders are <b>full COD</b> (06, owner 2026-06-27): nothing is charged online, so on placement
 * the order is confirmed immediately (→ CONFIRMED, stock decremented, receipt/dispatch enqueued) rather
 * than waiting for a PayHere/bank payment. Lorry orders are ignored here — they stay PENDING_PAYMENT
 * until paid online. Runs synchronously inside the placing transaction; idempotent via the confirmer.
 */
@Component
public class CourierOrderConfirmer {

    private final OrderRepository orders;
    private final OrderFulfilmentConfirmer confirmer;

    public CourierOrderConfirmer(OrderRepository orders, OrderFulfilmentConfirmer confirmer) {
        this.orders = orders;
        this.confirmer = confirmer;
    }

    @EventListener
    public void onOrderPlaced(OrderPlacedEvent event) {
        Order order = orders.findById(event.orderId()).orElse(null);
        if (order == null || !"COURIER".equals(order.getDeliveryMethod())) {
            return;                                  // lorry / non-courier → wait for online payment
        }
        if (order.getStatus() == OrderStatus.PENDING_PAYMENT) {
            confirmer.confirmPaidAndDecrement(order, ActorType.SYSTEM, null,
                "Courier order placed — full COD (nothing charged online)");
        }
    }
}
