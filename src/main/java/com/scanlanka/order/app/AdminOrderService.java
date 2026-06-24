package com.scanlanka.order.app;

import com.scanlanka.admin.app.AuditService;
import com.scanlanka.notification.app.OrderNotificationComposer;
import com.scanlanka.order.app.receipt.ReceiptService;
import com.scanlanka.order.domain.Order;
import com.scanlanka.order.domain.OrderItem;
import com.scanlanka.order.domain.OrderItemStatus;
import com.scanlanka.order.domain.OrderStatus;
import com.scanlanka.order.domain.OrderStatusEvent.ActorType;
import com.scanlanka.order.infra.OrderItemRepository;
import com.scanlanka.order.infra.OrderRepository;
import com.scanlanka.order.infra.OrderStatusEventRepository;
import com.scanlanka.payment.infra.BankTransferSlipRepository;
import com.scanlanka.payment.infra.PaymentRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Admin order management (08 FR-ADMIN-5/6/9). */
@Service
public class AdminOrderService {

    private final OrderRepository orders;
    private final OrderItemRepository items;
    private final OrderStatusEventRepository events;
    private final OrderService orderService;
    private final AuditService audit;
    private final ReceiptService receipts;
    private final PaymentRepository payments;
    private final BankTransferSlipRepository slips;
    private final OrderNotificationComposer notifications;

    public AdminOrderService(OrderRepository orders, OrderItemRepository items,
                             OrderStatusEventRepository events, OrderService orderService,
                             AuditService audit, ReceiptService receipts, PaymentRepository payments,
                             BankTransferSlipRepository slips, OrderNotificationComposer notifications) {
        this.orders = orders;
        this.items = items;
        this.events = events;
        this.orderService = orderService;
        this.audit = audit;
        this.receipts = receipts;
        this.payments = payments;
        this.slips = slips;
        this.notifications = notifications;
    }

    public record OrderSummary(String orderNumber, String status, long totalCents, String contactName,
                               String contactEmail, String fulfilmentType, Instant createdAt) {}
    public record ShipAddress(String street, String city, String province, String postalCode) {}
    public record OrderLineView(long id, String sku, String name, String spec, int quantity,
                                long unitPriceCents, long lineTotalCents, String status) {}
    public record StatusEventView(String fromStatus, String toStatus, Instant at, String note) {}
    public record PaymentView(String method, String status, String slipUrl, String slipReviewStatus) {}
    public record OrderDetailView(String orderNumber, String status, String contactName, String contactEmail,
                                  String contactPhone, String fulfilmentType, String deliveryPayment,
                                  long subtotalCents, long deliveryCents, long taxCents, long totalCents,
                                  long deliveryCodCents, Long actualDeliveryCents, String deliveryCourier,
                                  ShipAddress ship, List<OrderLineView> lines, List<StatusEventView> timeline,
                                  PaymentView payment) {}
    public record DispatchLine(String sku, String name, int quantity, String handlingClass) {}
    public record DispatchSummary(String orderNumber, String fulfilmentType, String deliveryPayment,
                                  ShipAddress ship, long totalCents, long deliveryCodCents, List<DispatchLine> lines) {}
    public record DashboardCounts(long pendingPayment, long awaitingBank, long paid, long completed) {}

    @Transactional(readOnly = true)
    public Page<OrderSummary> list(String view, String q, Instant from, Instant to, Pageable pageable) {
        return orders.findAll(boardSpec(view, q, from, to), pageable).map(this::toSummary);
    }

    @Transactional(readOnly = true)
    public List<OrderSummary> customerOrders(long customerId) {
        return orders.findByCustomerIdOrderByCreatedAtDesc(customerId).stream().map(this::toSummary).toList();
    }

    @Transactional(readOnly = true)
    public OrderDetailView detail(String orderNumber) {
        Order o = load(orderNumber);
        List<OrderLineView> lines = items.findByOrderId(o.getId()).stream().map(this::toLine).toList();
        List<StatusEventView> timeline = events.findByOrderIdOrderByCreatedAtAsc(o.getId()).stream()
            .map(e -> new StatusEventView(e.getFromStatus(), e.getToStatus(), e.getCreatedAt(), e.getNote()))
            .toList();
        return new OrderDetailView(o.getOrderNumber(), o.getStatus().name(), o.getContactName(),
            o.getContactEmail(), o.getContactPhone(), o.getFulfilmentType().name(), o.getDeliveryPayment().name(),
            o.getSubtotalCents(), o.getDeliveryCents(), o.getTaxCents(), o.getTotalCents(),
            o.getDeliveryCodCents(), o.getActualDeliveryCents(), o.getDeliveryCourier(),
            ship(o), lines, timeline, paymentView(o));
    }

    @Transactional
    public void updateStatus(String orderNumber, String to, Long adminId, String note) {
        Order o = load(orderNumber);
        String from = o.getStatus().name();
        orderService.transition(o.getId(), parseStatus(to), ActorType.ADMIN, adminId, note);
        audit.log(adminId, "ORDER_STATUS", "order", orderNumber, from, to);
    }

    @Transactional
    public void updateItemStatus(String orderNumber, long itemId, String to, Long adminId, String note) {
        Order o = load(orderNumber);
        OrderItem item = items.findById(itemId)
            .filter(i -> i.getOrderId().equals(o.getId()))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Item not found"));
        String from = item.getStatus();
        item.setStatus(parseItemStatus(to));
        items.save(item);
        audit.log(adminId, "ITEM_STATUS", "order_item", String.valueOf(itemId), from, to);
    }

    @Transactional
    public void recordDeliveryActual(String orderNumber, long actualCents, String courier, Long adminId) {
        Order o = load(orderNumber);
        String before = o.getActualDeliveryCents() != null ? o.getActualDeliveryCents().toString() : null;
        o.setActualDelivery(actualCents, courier);
        orders.save(o);
        audit.log(adminId, "DELIVERY_ACTUAL", "order", orderNumber, before, String.valueOf(actualCents));
    }

    @Transactional
    public void resendReceipt(String orderNumber, Long adminId) {
        Order o = load(orderNumber);
        notifications.resendReceipt(o, items.findByOrderId(o.getId()));
        audit.log(adminId, "RECEIPT_RESEND", "order", orderNumber, null, "enqueued");
    }

    @Transactional(readOnly = true)
    public DashboardCounts dashboardCounts() {
        return new DashboardCounts(
            orders.countByStatus(OrderStatus.PENDING_PAYMENT),
            orders.countByStatus(OrderStatus.AWAITING_BANK_CONFIRMATION),
            orders.countByStatus(OrderStatus.PAID),
            orders.countByStatus(OrderStatus.COMPLETED));
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

    @Transactional(readOnly = true)
    public ResponseEntity<byte[]> receiptPdf(String orderNumber) {
        Order o = load(orderNumber);
        byte[] pdf = receipts.ensurePdf(o, items.findByOrderId(o.getId()));
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"SL-" + o.getOrderNumber() + "-receipt.pdf\"")
            .contentType(MediaType.APPLICATION_PDF)
            .body(pdf);
    }

    private Specification<Order> boardSpec(String view, String q, Instant from, Instant to) {
        List<Specification<Order>> parts = new ArrayList<>();
        List<OrderStatus> statuses = statusesForView(view);
        if (statuses != null) {
            parts.add((root, query, cb) -> root.get("status").in(statuses));
        }
        if (q != null && !q.isBlank()) {
            String like = "%" + q.toLowerCase().trim() + "%";
            parts.add((root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("orderNumber")), like),
                cb.like(cb.lower(root.get("contactEmail")), like),
                cb.like(cb.lower(root.get("contactName")), like)));
        }
        if (from != null) {
            parts.add((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("createdAt"), from));
        }
        if (to != null) {
            parts.add((root, query, cb) -> cb.lessThanOrEqualTo(root.get("createdAt"), to));
        }
        return parts.stream().reduce(Specification.where(null), Specification::and);
    }

    private static List<OrderStatus> statusesForView(String view) {
        if (view == null || view.isBlank() || "all".equalsIgnoreCase(view)) return null;
        return switch (view.toLowerCase()) {
            case "pending_payment" -> List.of(
                OrderStatus.PENDING_PAYMENT, OrderStatus.AWAITING_BANK_CONFIRMATION,
                OrderStatus.BANK_SLIP_REJECTED, OrderStatus.PAYMENT_FAILED);
            case "paid" -> List.of(OrderStatus.PAID, OrderStatus.CONFIRMED);
            case "in_fulfilment" -> List.of(
                OrderStatus.PACKED, OrderStatus.SHIPPED, OrderStatus.READY_FOR_PICKUP, OrderStatus.DELIVERY_FAILED);
            case "delivered" -> List.of(OrderStatus.COMPLETED);
            case "cancelled" -> List.of(OrderStatus.CANCELLED);
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_VIEW");
        };
    }

    private OrderSummary toSummary(Order o) {
        return new OrderSummary(o.getOrderNumber(), o.getStatus().name(), o.getTotalCents(),
            o.getContactName(), o.getContactEmail(), o.getFulfilmentType().name(), o.getCreatedAt());
    }

    private OrderLineView toLine(OrderItem i) {
        String spec = i.getHandlingClassSnapshot();
        return new OrderLineView(i.getId(), i.getSkuSnapshot(), i.getNameSnapshot(),
            spec == null || spec.isBlank() ? "—" : spec, i.getQuantity(),
            i.getUnitPriceCents(), i.getLineTotalCents(), i.getStatus());
    }

    private PaymentView paymentView(Order o) {
        return payments.findByOrderId(o.getId()).map(p -> {
            var slip = slips.findFirstByPaymentIdOrderByUploadedAtDesc(p.getId()).orElse(null);
            return new PaymentView(p.getMethod(), p.getStatus(),
                slip != null ? slip.getUrl() : null,
                slip != null ? slip.getReviewStatus() : null);
        }).orElse(null);
    }

    private Order load(String orderNumber) {
        return orders.findByOrderNumber(orderNumber)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));
    }

    private static ShipAddress ship(Order o) {
        return new ShipAddress(o.getShipStreet(), o.getShipCity(), o.getShipProvince(), o.getShipPostalCode());
    }

    private static OrderStatus parseStatus(String to) {
        try {
            return OrderStatus.valueOf(to);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_STATUS");
        }
    }

    private static OrderItemStatus parseItemStatus(String to) {
        try {
            return OrderItemStatus.valueOf(to);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_ITEM_STATUS");
        }
    }
}
