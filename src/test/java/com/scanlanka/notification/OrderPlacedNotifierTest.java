package com.scanlanka.notification;

import com.scanlanka.notification.app.OrderNotificationComposer;
import com.scanlanka.notification.app.OrderPlacedNotifier;
import com.scanlanka.order.app.OrderPlacedEvent;
import com.scanlanka.order.domain.DeliveryPayment;
import com.scanlanka.order.domain.Order;
import com.scanlanka.order.infra.OrderItemRepository;
import com.scanlanka.order.infra.OrderRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrderPlacedNotifierTest {

    private final OrderRepository orders = mock(OrderRepository.class);
    private final OrderItemRepository items = mock(OrderItemRepository.class);
    private final OrderNotificationComposer composer = mock(OrderNotificationComposer.class);
    private final OrderPlacedNotifier notifier = new OrderPlacedNotifier(orders, items, composer);

    @Test
    void emailsAtPlacementForNonCodOrders() {
        Order order = order(7L, DeliveryPayment.PREPAID);
        when(orders.findById(7L)).thenReturn(Optional.of(order));
        when(items.findByOrderId(7L)).thenReturn(List.of());

        notifier.onOrderPlaced(new OrderPlacedEvent(7L));

        verify(composer, times(1)).onOrderPlaced(order, List.of());
    }

    @Test
    void skipsCodOrders() {
        Order order = order(9L, DeliveryPayment.COD);
        when(orders.findById(9L)).thenReturn(Optional.of(order));

        notifier.onOrderPlaced(new OrderPlacedEvent(9L));

        verify(composer, never()).onOrderPlaced(org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any());
    }

    @Test
    void skipsWhenOrderMissing() {
        when(orders.findById(1L)).thenReturn(Optional.empty());
        notifier.onOrderPlaced(new OrderPlacedEvent(1L));
        verify(composer, never()).onOrderPlaced(org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any());
    }

    private static Order order(long id, DeliveryPayment payment) {
        Order order = mock(Order.class);
        when(order.getId()).thenReturn(id);
        when(order.getDeliveryPayment()).thenReturn(payment);
        return order;
    }
}
