package com.scanlanka.order.app;

import com.scanlanka.admin.app.AuditService;
import com.scanlanka.order.domain.Order;
import com.scanlanka.order.domain.OrderItem;
import com.scanlanka.order.domain.OrderStatus;
import com.scanlanka.order.domain.OrderStatusEvent.ActorType;
import com.scanlanka.order.infra.OrderItemRepository;
import com.scanlanka.order.infra.OrderRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/** Admin order management (08 FR-ADMIN-5/6/9): board, detail, status transitions (audited), dispatch. */
@Service
public class AdminOrderService {

    private final OrderRepository orders;
    private final OrderItemRepository items;
    private final OrderService orderService;
    private final AuditService audit;

    public AdminOrderService(OrderRepository orders, OrderItemRepository items,
                             OrderService orderService, AuditService audit) {
        this.orders = orders;
        this.items = items;
        this.orderService = orderService;
        this.audit = audit;
    }

    public record OrderSummary(String orderNumber, String status, long totalCents,
                               String contactName, String fulfilmentType) {}
    public record ShipAddress(String street, String city, String province, String postalCode) {}
    public record OrderLineView(String sku, String name, int quantity, long unitPriceCents, long lineTotalCents) {}
    public record OrderDetailView(String orderNumber, String status, String contactName, String contactEmail,
                                  String contactPhone, String fulfilmentType, String deliveryPayment,
                                  long subtotalCents, long deliveryCents, long taxCents, long totalCents,
                                  long deliveryCodCents, ShipAddress ship, List<OrderLineView> lines) {}
    public record DispatchLine(String sku, String name, int quantity, String handlingClass) {}
    public record DispatchSummary(String orderNumber, String fulfilmentType, String deliveryPayment,
                                  ShipAddress ship, long totalCents, long deliveryCodCents, List<DispatchLine> lines) {}

    @Transactional(readOnly = true)
    public Page<OrderSummary> list(String status, Pageable pageable) {
        Page<Order> page = (status == null || status.isBlank() || "all".equalsIgnoreCase(status))
            ? orders.findAllByOrderByCreatedAtDesc(pageable)
            : orders.findByStatusOrderByCreatedAtDesc(status, pageable);
        return page.map(o -> new OrderSummary(o.getOrderNumber(), o.getStatus().name(), o.getTotalCents(),
            o.getContactName(), o.getFulfilmentType().name()));
    }

    @Transactional(readOnly = true)
    public OrderDetailView detail(String orderNumber) {
        Order o = load(orderNumber);
        List<OrderLineView> lines = items.findByOrderId(o.getId()).stream()
            .map(i -> new OrderLineView(i.getSkuSnapshot(), i.getNameSnapshot(), i.getQuantity(),
                i.getUnitPriceCents(), i.getLineTotalCents())).toList();
        return new OrderDetailView(o.getOrderNumber(), o.getStatus().name(), o.getContactName(),
            o.getContactEmail(), null, o.getFulfilmentType().name(), o.getDeliveryPayment().name(),
            o.getSubtotalCents(), o.getDeliveryCents(), o.getTaxCents(), o.getTotalCents(),
            o.getDeliveryCodCents(), ship(o), lines);
    }

    @Transactional
    public void updateStatus(String orderNumber, String to, Long adminId, String note) {
        Order o = load(orderNumber);
        OrderStatus target;
        try {
            target = OrderStatus.valueOf(to);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_STATUS");
        }
        String from = o.getStatus().name();
        orderService.transition(o.getId(), target, ActorType.ADMIN, adminId, note);
        audit.log(adminId, "ORDER_STATUS", "order", orderNumber, from, to);
    }

    public record DashboardCounts(long pendingPayment, long awaitingBank, long paid, long completed) {}

    @Transactional(readOnly = true)
    public DashboardCounts dashboardCounts() {
        return new DashboardCounts(
            orders.countByStatus(OrderStatus.PENDING_PAYMENT.name()),
            orders.countByStatus(OrderStatus.AWAITING_BANK_CONFIRMATION.name()),
            orders.countByStatus(OrderStatus.PAID.name()),
            orders.countByStatus(OrderStatus.COMPLETED.name()));
    }

    @Transactional(readOnly = true)
    public DispatchSummary dispatchSummary(String orderNumber) {
        Order o = load(orderNumber);
        List<DispatchLine> lines = items.findByOrderId(o.getId()).stream()
            .map(i -> new DispatchLine(i.getSkuSnapshot(), i.getNameSnapshot(), i.getQuantity(),
                i.getHandlingClassSnapshot())).toList();
        return new DispatchSummary(o.getOrderNumber(), o.getFulfilmentType().name(),
            o.getDeliveryPayment().name(), ship(o), o.getTotalCents(), o.getDeliveryCodCents(), lines);
    }

    private Order load(String orderNumber) {
        return orders.findByOrderNumber(orderNumber)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));
    }

    private static ShipAddress ship(Order o) {
        return new ShipAddress(o.getShipStreet(), o.getShipCity(), o.getShipProvince(), o.getShipPostalCode());
    }
}
