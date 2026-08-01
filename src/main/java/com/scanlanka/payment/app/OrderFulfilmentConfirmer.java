package com.scanlanka.payment.app;

import com.scanlanka.catalog.app.StockService;
import com.scanlanka.checkout.app.StockReservationService;
import com.scanlanka.notification.app.OrderNotificationComposer;
import com.scanlanka.order.app.OrderService;
import com.scanlanka.order.domain.DeliveryPayment;
import com.scanlanka.order.domain.Order;
import com.scanlanka.order.domain.OrderItem;
import com.scanlanka.order.domain.OrderStatus;
import com.scanlanka.order.domain.OrderStatusEvent.ActorType;
import com.scanlanka.order.infra.OrderItemRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Shared "payment confirmed" step (06) used by PayHere notify AND bank-transfer admin confirm:
 * guarded transition to PAID/CONFIRMED + atomic stock decrement + enqueue receipt & dispatch emails
 * (10, outbox idempotent). Idempotent overall.
 */
@Service
public class OrderFulfilmentConfirmer {

    private final OrderService orderService;
    private final OrderItemRepository orderItems;
    private final StockService stock;
    private final StockReservationService reservations;
    private final OrderNotificationComposer orderNotifications;

    public OrderFulfilmentConfirmer(OrderService orderService, OrderItemRepository orderItems, StockService stock,
                                    StockReservationService reservations, OrderNotificationComposer orderNotifications) {
        this.orderService = orderService;
        this.orderItems = orderItems;
        this.stock = stock;
        this.reservations = reservations;
        this.orderNotifications = orderNotifications;
    }

    /** @return true if it confirmed (was pending/cancelled-awaiting-notify); false if already confirmed. */
    @Transactional
    public boolean confirmPaidAndDecrement(Order order, ActorType actorType, Long actorId, String note) {
        OrderStatus from = order.getStatus();
        // CANCELLED is allowed so a delayed PayHere notify after auto-cancel still fulfils a real payment.
        if (from != OrderStatus.PENDING_PAYMENT
            && from != OrderStatus.AWAITING_BANK_CONFIRMATION
            && from != OrderStatus.CANCELLED) {
            return false;
        }
        OrderStatus target = order.getDeliveryPayment() == DeliveryPayment.COD
            ? OrderStatus.CONFIRMED : OrderStatus.PAID;
        orderService.transition(order.getId(), target, actorType, actorId, note);

        List<OrderItem> items = orderItems.findByOrderId(order.getId());
        for (OrderItem item : items) {
            if (item.getProductId() != null) {
                stock.decrement(item.getProductId(), item.getVariantId(), item.getQuantity());
            }
        }
        reservations.consumeForOrder(order.getId());
        orderNotifications.onOrderConfirmed(order, items);
        return true;
    }
}
