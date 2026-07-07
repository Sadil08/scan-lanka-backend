package com.scanlanka.payment.app;

import com.scanlanka.order.app.OrderPlacedEvent;
import com.scanlanka.order.domain.DeliveryPayment;
import com.scanlanka.order.domain.Order;
import com.scanlanka.order.domain.OrderStatus;
import com.scanlanka.order.domain.OrderStatusEvent.ActorType;
import com.scanlanka.order.infra.OrderRepository;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * COD orders are confirmed immediately on placement (06, owner 2026-06-27 / 2026-07-03): nothing is
 * charged online, so there is no PayHere/bank payment to wait for. This covers <b>both</b> rails paid
 * on delivery — courier (always full COD) and the in-house lorry when the customer chose cash on
 * delivery. Lorry-online orders (PREPAID) are ignored here; they stay PENDING_PAYMENT until paid.
 * Runs synchronously inside the placing transaction; idempotent via the confirmer.
 */
@Component
public class CodOrderConfirmer {

    private final OrderRepository orders;
    private final OrderFulfilmentConfirmer confirmer;

    public CodOrderConfirmer(OrderRepository orders, OrderFulfilmentConfirmer confirmer) {
        this.orders = orders;
        this.confirmer = confirmer;
    }

    @EventListener
    public void onOrderPlaced(OrderPlacedEvent event) {
        Order order = orders.findById(event.orderId()).orElse(null);
        if (order == null || order.getDeliveryPayment() != DeliveryPayment.COD) {
            return;                                  // lorry-online / prepaid → wait for online payment
        }
        if (order.getStatus() == OrderStatus.PENDING_PAYMENT) {
            String note = "COURIER".equals(order.getDeliveryMethod())
                ? "Courier order placed — full COD (nothing charged online)"
                : "Lorry order placed — cash on delivery (nothing charged online)";
            confirmer.confirmPaidAndDecrement(order, ActorType.SYSTEM, null, note);
        }
    }
}
