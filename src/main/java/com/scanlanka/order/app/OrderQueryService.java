package com.scanlanka.order.app;

import com.scanlanka.order.domain.Order;
import com.scanlanka.order.domain.OrderItem;
import com.scanlanka.order.domain.OrderStatusEvent;
import com.scanlanka.order.infra.OrderItemRepository;
import com.scanlanka.order.infra.OrderRepository;
import com.scanlanka.order.infra.OrderStatusEventRepository;
import com.scanlanka.order.web.dto.OrderResponses.OrderDetailView;
import com.scanlanka.order.web.dto.OrderResponses.OrderLineView;
import com.scanlanka.order.web.dto.OrderResponses.OrderSummaryView;
import com.scanlanka.order.web.dto.OrderResponses.StatusEventView;
import com.scanlanka.returns.infra.RefundRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/** Order reads for customers and guests (09). */
@Service
public class OrderQueryService {

    private final OrderRepository orders;
    private final OrderItemRepository items;
    private final OrderStatusEventRepository events;
    private final OrderNumberService orderNumbers;
    private final RefundRepository refunds;

    public OrderQueryService(OrderRepository orders, OrderItemRepository items,
                             OrderStatusEventRepository events, OrderNumberService orderNumbers,
                             RefundRepository refunds) {
        this.orders = orders;
        this.items = items;
        this.events = events;
        this.orderNumbers = orderNumbers;
        this.refunds = refunds;
    }

    public record OrderStatusView(String orderNumber, String status, long totalCents) {}

    @Transactional(readOnly = true)
    public List<OrderSummaryView> listForCustomer(long customerId) {
        return orders.findByCustomerIdOrderByCreatedAtDesc(customerId).stream()
            .map(o -> new OrderSummaryView(o.getOrderNumber(), o.getStatus().name(),
                o.getTotalCents(), refunds.sumAmountByOrderId(o.getId()), o.getCreatedAt()))
            .toList();
    }

    @Transactional(readOnly = true)
    public OrderDetailView detailForCustomer(long customerId, String orderNumber) {
        Order o = orders.findByOrderNumber(orderNumber)
            .filter(x -> x.getCustomerId() != null && x.getCustomerId().equals(customerId))
            .orElseThrow(OrderQueryService::notFound);
        return toDetail(o);
    }

    @Transactional(readOnly = true)
    public OrderStatusView lookup(String orderNumber, String email) {
        if (!orderNumbers.verify(orderNumber)) throw notFound();
        Order o = orders.findByOrderNumber(orderNumber)
            .filter(x -> x.getContactEmail() != null && x.getContactEmail().equalsIgnoreCase(email))
            .orElseThrow(OrderQueryService::notFound);
        return new OrderStatusView(o.getOrderNumber(), o.getStatus().name(), o.getTotalCents());
    }

    @Transactional(readOnly = true)
    public OrderDetailView lookupDetail(String orderNumber, String email) {
        if (!orderNumbers.verify(orderNumber)) throw notFound();
        Order o = orders.findByOrderNumber(orderNumber)
            .filter(x -> x.getContactEmail() != null && x.getContactEmail().equalsIgnoreCase(email))
            .orElseThrow(OrderQueryService::notFound);
        return toDetail(o);
    }

    private OrderDetailView toDetail(Order o) {
        List<OrderLineView> lines = items.findByOrderId(o.getId()).stream()
            .map(this::toLine).toList();
        List<StatusEventView> timeline = events.findByOrderIdOrderByCreatedAtAsc(o.getId()).stream()
            .map(e -> new StatusEventView(e.getFromStatus(), e.getToStatus(), e.getCreatedAt()))
            .toList();
        return new OrderDetailView(
            o.getOrderNumber(), o.getStatus().name(), o.getSubtotalCents(), o.getDeliveryCents(),
            o.getTaxCents(), o.getTotalCents(), refunds.sumAmountByOrderId(o.getId()), o.getDeliveryCodCents(),
            o.getFulfilmentType().name(), o.getDeliveryPayment().name(),
            o.getDeliveryMethod(), o.getCourierEstimateCents(),
            o.getCarrier(), o.getTrackingRef(),
            o.getShipStreet(), o.getShipCity(), o.getShipProvince(), o.getShipPostalCode(),
            lines, timeline);
    }

    private OrderLineView toLine(OrderItem item) {
        return new OrderLineView(item.getNameSnapshot(), item.getSkuSnapshot(), item.getQuantity(),
            item.getUnitPriceCents(), item.getLineTotalCents());
    }

    private static ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Not found");
    }
}
