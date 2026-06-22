package com.scanlanka.order.app;

import com.scanlanka.order.domain.Order;
import com.scanlanka.order.infra.OrderRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Guest order-status lookup (09 FR-ORDER-7): order# must verify (HMAC) AND match the contact email. */
@Service
public class OrderQueryService {

    private final OrderRepository orders;
    private final OrderNumberService orderNumbers;

    public OrderQueryService(OrderRepository orders, OrderNumberService orderNumbers) {
        this.orders = orders;
        this.orderNumbers = orderNumbers;
    }

    public record OrderStatusView(String orderNumber, String status, long totalCents) {}

    @Transactional(readOnly = true)
    public OrderStatusView lookup(String orderNumber, String email) {
        if (!orderNumbers.verify(orderNumber)) throw notFound();   // forged → 404 (no oracle)
        Order o = orders.findByOrderNumber(orderNumber)
            .filter(x -> x.getContactEmail() != null && x.getContactEmail().equalsIgnoreCase(email))
            .orElseThrow(OrderQueryService::notFound);
        return new OrderStatusView(o.getOrderNumber(), o.getStatus().name(), o.getTotalCents());
    }

    private static ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Not found");
    }
}
