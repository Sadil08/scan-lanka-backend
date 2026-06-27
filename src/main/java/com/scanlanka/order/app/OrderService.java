package com.scanlanka.order.app;

import com.scanlanka.order.app.OrderCommands.CreateOrderCommand;
import com.scanlanka.order.app.OrderCommands.LineSnapshot;
import com.scanlanka.order.domain.Order;
import com.scanlanka.order.domain.OrderItem;
import com.scanlanka.order.domain.OrderStateMachine;
import com.scanlanka.order.domain.OrderStatus;
import com.scanlanka.order.domain.OrderStatusEvent;
import com.scanlanka.order.domain.OrderStatusEvent.ActorType;
import com.scanlanka.order.infra.OrderItemRepository;
import com.scanlanka.order.infra.OrderRepository;
import com.scanlanka.order.infra.OrderStatusEventRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;

/**
 * Order creation + lifecycle (09-orders). createDraft snapshots the lines + assigns a verifiable
 * number; transition is guarded by the state machine and audited. Stock decrement happens at PAID
 * confirmation in payments (06).
 */
@Service
public class OrderService {

    private final OrderRepository orders;
    private final OrderItemRepository items;
    private final OrderStatusEventRepository events;
    private final OrderNumberService orderNumbers;

    public OrderService(OrderRepository orders, OrderItemRepository items,
                        OrderStatusEventRepository events, OrderNumberService orderNumbers) {
        this.orders = orders;
        this.items = items;
        this.events = events;
        this.orderNumbers = orderNumbers;
    }

    @Transactional
    public Order createDraft(CreateOrderCommand cmd) {
        Order order = new Order(uniqueOrderNumber(), cmd.customerId(), cmd.fulfilmentType());
        order.setGuestEmail(cmd.guestEmail());
        order.setContact(cmd.contactName(), cmd.contactPhone(), cmd.contactEmail());
        if (cmd.ship() != null) {
            order.setShipAddress(cmd.ship().street(), cmd.ship().city(), cmd.ship().province(), cmd.ship().postalCode());
        }
        if (cmd.billing() != null) {
            var b = cmd.billing();
            order.setBilling(b.name(), b.taxId(), b.street(), b.city(), b.province(), b.postalCode());
        }
        order.setTotals(cmd.subtotalCents(), cmd.discountCents(), cmd.deliveryCents(), cmd.taxCents(), cmd.totalCents());
        order.setDeliveryPayment(cmd.deliveryPayment());
        order.setDeliveryCodCents(cmd.deliveryCodCents());
        order.setDeliveryMethod(cmd.deliveryMethod());
        order.setCourierEstimateCents(cmd.courierEstimateCents());
        order.setDeliveryArranged(cmd.deliveryArranged());
        order.setStatus(OrderStatus.PENDING_PAYMENT);
        order = orders.save(order);

        for (LineSnapshot l : cmd.lines()) {
            items.save(new OrderItem(order.getId(), l.productId(), l.variantId(), l.sku(), l.name(),
                l.handlingClass(), l.unitPriceCents(), l.quantity(), l.lineTotalCents()));
        }
        events.save(new OrderStatusEvent(order.getId(), null, OrderStatus.PENDING_PAYMENT,
            ActorType.SYSTEM, null, "created"));
        return order;
    }

    /** Guarded, audited status change (09 FR-ORDER-3). */
    @Transactional
    public void transition(Long orderId, OrderStatus to, ActorType actorType, Long actorId, String note) {
        Order order = orders.findById(orderId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));
        OrderStatus from = order.getStatus();
        if (!OrderStateMachine.canTransition(from, to)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "ILLEGAL_TRANSITION");
        }
        order.setStatus(to);
        if (to == OrderStatus.PAID) order.setPaidAt(Instant.now());
        if (to == OrderStatus.CONFIRMED) order.setConfirmedAt(Instant.now());
        // saveAndFlush: the status change must hit the DB now. Callers (e.g. payment confirm) run a
        // subsequent @Modifying(clearAutomatically=true) stock query that would otherwise clear this
        // still-dirty entity from the persistence context and silently drop the status UPDATE.
        orders.saveAndFlush(order);
        events.save(new OrderStatusEvent(orderId, from, to, actorType, actorId, note));
    }

    private String uniqueOrderNumber() {
        for (int i = 0; i < 5; i++) {
            String candidate = orderNumbers.generate();
            if (orders.findByOrderNumber(candidate).isEmpty()) return candidate;
        }
        throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "could not allocate order number");
    }
}
