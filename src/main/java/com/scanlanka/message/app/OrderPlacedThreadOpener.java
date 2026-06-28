package com.scanlanka.message.app;

import com.scanlanka.order.app.OrderPlacedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** Opens an order message thread on placement (19 FR-MSG-1). */
@Component
public class OrderPlacedThreadOpener {

    private final OrderMessageService messages;

    public OrderPlacedThreadOpener(OrderMessageService messages) {
        this.messages = messages;
    }

    @EventListener
    public void onOrderPlaced(OrderPlacedEvent event) {
        messages.openForOrder(event.orderId());
    }
}
