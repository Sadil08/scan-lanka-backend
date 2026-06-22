package com.scanlanka.payment.app;

import com.scanlanka.catalog.app.StockService;
import com.scanlanka.notification.app.NotificationService;
import com.scanlanka.order.app.OrderService;
import com.scanlanka.order.domain.DeliveryPayment;
import com.scanlanka.order.domain.Order;
import com.scanlanka.order.domain.OrderItem;
import com.scanlanka.order.domain.OrderStatus;
import com.scanlanka.order.domain.OrderStatusEvent.ActorType;
import com.scanlanka.order.infra.OrderItemRepository;
import org.springframework.beans.factory.annotation.Value;
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
    private final NotificationService notifications;
    private final String adminEmail;

    public OrderFulfilmentConfirmer(OrderService orderService, OrderItemRepository orderItems, StockService stock,
                                    NotificationService notifications,
                                    @Value("${app.notifications.admin-email}") String adminEmail) {
        this.orderService = orderService;
        this.orderItems = orderItems;
        this.stock = stock;
        this.notifications = notifications;
        this.adminEmail = adminEmail;
    }

    /** @return true if it confirmed (was pending); false if already confirmed (idempotent no-op). */
    @Transactional
    public boolean confirmPaidAndDecrement(Order order, ActorType actorType, Long actorId, String note) {
        OrderStatus from = order.getStatus();
        if (from != OrderStatus.PENDING_PAYMENT && from != OrderStatus.AWAITING_BANK_CONFIRMATION) {
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

        // receipt → customer, dispatch summary → admin (idempotent outbox; emailed by the worker)
        notifications.enqueue("ORDER_RECEIPT", order.getContactEmail(),
            "Your Scan Lanka receipt — " + order.getOrderNumber(), receiptBody(order, items),
            "receipt:" + order.getId());
        notifications.enqueue("ADMIN_DISPATCH", adminEmail,
            "New order " + order.getOrderNumber(), dispatchBody(order, items),
            "dispatch:" + order.getId());
        return true;
    }

    private static String receiptBody(Order o, List<OrderItem> items) {
        StringBuilder b = new StringBuilder();
        b.append("Thank you for your order ").append(o.getOrderNumber()).append(".\n\n");
        for (OrderItem it : items) {
            b.append(it.getNameSnapshot()).append(" x").append(it.getQuantity())
                .append("  ").append(lkr(it.getLineTotalCents())).append("\n");
        }
        b.append("\nSubtotal: ").append(lkr(o.getSubtotalCents()));
        if (o.getDeliveryPayment() == DeliveryPayment.COD) {
            b.append("\nDelivery (pay on delivery): ").append(lkr(o.getDeliveryCodCents()));
        } else {
            b.append("\nDelivery: ").append(lkr(o.getDeliveryCents()));
        }
        b.append("\nTax: ").append(lkr(o.getTaxCents()));
        b.append("\nPaid online: ").append(lkr(o.getTotalCents())).append("\n");
        return b.toString();
    }

    private static String dispatchBody(Order o, List<OrderItem> items) {
        StringBuilder b = new StringBuilder();
        b.append("New order ").append(o.getOrderNumber())
            .append(" from ").append(o.getContactName()).append(" (").append(o.getContactEmail()).append(")\n");
        b.append("Fulfilment: ").append(o.getFulfilmentType()).append("\n\n");
        for (OrderItem it : items) {
            b.append("[").append(it.getSkuSnapshot()).append("] ").append(it.getNameSnapshot())
                .append(" x").append(it.getQuantity()).append("\n");
        }
        b.append("\nTotal paid online: ").append(lkr(o.getTotalCents()));
        if (o.getDeliveryPayment() == DeliveryPayment.COD) {
            b.append("\nCollect on delivery: ").append(lkr(o.getDeliveryCodCents()));
        }
        return b.toString();
    }

    private static String lkr(long cents) {
        return "Rs " + (cents / 100);
    }
}
