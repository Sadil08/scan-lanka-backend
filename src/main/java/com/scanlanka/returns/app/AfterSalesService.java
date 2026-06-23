package com.scanlanka.returns.app;

import com.scanlanka.admin.app.AuditService;
import com.scanlanka.auth.app.StepUpService;
import com.scanlanka.auth.app.StepUpService.StepUpCredentials;
import com.scanlanka.catalog.app.StockService;
import com.scanlanka.checkout.app.StockReservationService;
import com.scanlanka.order.app.OrderService;
import com.scanlanka.order.domain.Order;
import com.scanlanka.order.domain.OrderItem;
import com.scanlanka.order.domain.OrderStateMachine;
import com.scanlanka.order.domain.OrderStatus;
import com.scanlanka.order.domain.OrderStatusEvent.ActorType;
import com.scanlanka.order.infra.OrderItemRepository;
import com.scanlanka.order.infra.OrderRepository;
import com.scanlanka.returns.domain.Refund;
import com.scanlanka.returns.domain.Refund.Method;
import com.scanlanka.returns.domain.RefundItem;
import com.scanlanka.returns.domain.RefundItem.Disposition;
import com.scanlanka.returns.infra.RefundItemRepository;
import com.scanlanka.returns.infra.RefundRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/** Admin after-sales: cancel order + record refund (16-returns-refunds). */
@Service
public class AfterSalesService {

    private static final Set<OrderStatus> STOCK_DECREMENTED = EnumSet.of(
        OrderStatus.PAID, OrderStatus.CONFIRMED, OrderStatus.PACKED,
        OrderStatus.READY_FOR_PICKUP, OrderStatus.DELIVERY_FAILED);

    private final OrderRepository orders;
    private final OrderItemRepository items;
    private final OrderService orderService;
    private final StockService stock;
    private final StockReservationService reservations;
    private final RefundRepository refunds;
    private final RefundItemRepository refundItems;
    private final StepUpService stepUp;
    private final AuditService audit;

    public AfterSalesService(OrderRepository orders, OrderItemRepository items, OrderService orderService,
                             StockService stock, StockReservationService reservations,
                             RefundRepository refunds, RefundItemRepository refundItems,
                             StepUpService stepUp, AuditService audit) {
        this.orders = orders;
        this.items = items;
        this.orderService = orderService;
        this.stock = stock;
        this.reservations = reservations;
        this.refunds = refunds;
        this.refundItems = refundItems;
        this.stepUp = stepUp;
        this.audit = audit;
    }

    public record CancelRequest(String reason, String note, String password, String totp) {}
    public record RefundLineRequest(long itemId, int quantity, String disposition) {}
    public record RecordRefundRequest(long amountCents, String method, String reason, String gatewayRef,
                                      String idempotencyKey, List<RefundLineRequest> items,
                                      String password, String totp) {}
    public record RefundItemView(long itemId, int quantity, String disposition) {}
    public record RefundView(long id, long amountCents, String method, String reason, String gatewayRef,
                             String status, Instant createdAt, List<RefundItemView> lines) {}

    @Transactional
    public void cancelOrder(String orderNumber, CancelRequest req, long adminId) {
        stepUp.require(adminId, new StepUpCredentials(req.password(), req.totp()));
        Order o = load(orderNumber);
        OrderStatus from = o.getStatus();
        if (!OrderStateMachine.canTransition(from, OrderStatus.CANCELLED)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "CANNOT_CANCEL");
        }
        orderService.transition(o.getId(), OrderStatus.CANCELLED, ActorType.ADMIN, adminId, cancelNote(req));
        if (STOCK_DECREMENTED.contains(from)) {
            restockOrder(o.getId());
        } else {
            reservations.releaseForOrder(o.getId());
        }
        audit.log(adminId, "ORDER_CANCEL", "order", orderNumber, from.name(), "CANCELLED");
    }

    @Transactional
    public RefundView recordRefund(String orderNumber, RecordRefundRequest req, long adminId) {
        stepUp.require(adminId, new StepUpCredentials(req.password(), req.totp()));
        if (req.idempotencyKey() != null && !req.idempotencyKey().isBlank()) {
            var existing = refunds.findByIdempotencyKey(req.idempotencyKey().trim());
            if (existing.isPresent()) return toView(existing.get());
        }
        Order o = load(orderNumber);
        long prior = refunds.sumAmountByOrderId(o.getId());
        long cap = o.getTotalCents() - prior;
        if (req.amountCents() > cap) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "REFUND_EXCEEDS_CAP");
        }
        if (req.amountCents() < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_AMOUNT");
        }
        String key = (req.idempotencyKey() != null && !req.idempotencyKey().isBlank())
            ? req.idempotencyKey().trim()
            : "refund:" + o.getId() + ":" + System.nanoTime();
        Refund refund = refunds.save(new Refund(o.getId(), req.amountCents(), parseMethod(req.method()),
            req.reason(), req.gatewayRef(), key, adminId));
        if (req.items() != null) {
            for (RefundLineRequest line : req.items()) {
                OrderItem item = items.findById(line.itemId())
                    .filter(i -> i.getOrderId().equals(o.getId()))
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_ITEM"));
                if (line.quantity() < 1 || line.quantity() > item.getQuantity()) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_QTY");
                }
                Disposition disp = parseDisposition(line.disposition());
                refundItems.save(new RefundItem(refund.getId(), item.getId(), line.quantity(), disp));
                if (disp == Disposition.RESTOCK && item.getProductId() != null) {
                    stock.increment(item.getProductId(), item.getVariantId(), line.quantity());
                }
            }
        }
        audit.log(adminId, "REFUND_RECORD", "order", orderNumber, String.valueOf(prior),
            String.valueOf(prior + req.amountCents()));
        return toView(refund);
    }

    @Transactional(readOnly = true)
    public List<RefundView> listRefunds(String orderNumber) {
        Order o = load(orderNumber);
        return refunds.findByOrderIdOrderByCreatedAtDesc(o.getId()).stream().map(this::toView).toList();
    }

    @Transactional(readOnly = true)
    public long refundTotalForOrder(Long orderId) {
        return refunds.sumAmountByOrderId(orderId);
    }

    private void restockOrder(Long orderId) {
        for (OrderItem item : items.findByOrderId(orderId)) {
            if (item.getProductId() != null) {
                stock.increment(item.getProductId(), item.getVariantId(), item.getQuantity());
            }
        }
    }

    private RefundView toView(Refund r) {
        List<RefundItemView> lines = refundItems.findByRefundId(r.getId()).stream()
            .map(ri -> new RefundItemView(ri.getOrderItemId(), ri.getQuantity(), ri.getDisposition()))
            .toList();
        return new RefundView(r.getId(), r.getAmountCents(), r.getMethod(), r.getReason(), r.getGatewayRef(),
            r.getStatus(), r.getCreatedAt(), lines);
    }

    private static String cancelNote(CancelRequest req) {
        if (req.reason() != null && !req.reason().isBlank()) {
            return req.note() != null ? req.reason() + ": " + req.note() : req.reason();
        }
        return req.note();
    }

    private Order load(String orderNumber) {
        return orders.findByOrderNumber(orderNumber)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));
    }

    private static Method parseMethod(String method) {
        try {
            return Method.valueOf(method);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_METHOD");
        }
    }

    private static Disposition parseDisposition(String disposition) {
        try {
            return Disposition.valueOf(disposition);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_DISPOSITION");
        }
    }
}
