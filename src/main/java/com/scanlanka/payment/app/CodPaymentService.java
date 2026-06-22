package com.scanlanka.payment.app;

import com.scanlanka.admin.app.AuditService;
import com.scanlanka.order.domain.DeliveryPayment;
import com.scanlanka.order.domain.Order;
import com.scanlanka.order.domain.OrderStatus;
import com.scanlanka.order.domain.OrderStatusEvent.ActorType;
import com.scanlanka.order.app.OrderService;
import com.scanlanka.order.infra.OrderRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Admin COD delivery-fee collection (08 FR-ADMIN-11). */
@Service
public class CodPaymentService {

    private final OrderRepository orders;
    private final OrderService orderService;
    private final AuditService audit;

    public CodPaymentService(OrderRepository orders, OrderService orderService, AuditService audit) {
        this.orders = orders;
        this.orderService = orderService;
        this.audit = audit;
    }

    @Transactional
    public void markCodReceived(String orderNumber, Long adminId) {
        Order order = orders.findByOrderNumber(orderNumber)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));
        if (order.getDeliveryPayment() != DeliveryPayment.COD) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "NOT_COD_ORDER");
        }
        OrderStatus status = order.getStatus();
        if (status != OrderStatus.SHIPPED && status != OrderStatus.READY_FOR_PICKUP
            && status != OrderStatus.CONFIRMED && status != OrderStatus.PAID) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "INVALID_STATUS_FOR_COD");
        }
        if (status != OrderStatus.COMPLETED) {
            orderService.transition(order.getId(), OrderStatus.COMPLETED, ActorType.ADMIN, adminId,
                "COD delivery fee received");
        }
        audit.log(adminId, "COD_RECEIVED", "order", orderNumber, status.name(), OrderStatus.COMPLETED.name());
    }
}
