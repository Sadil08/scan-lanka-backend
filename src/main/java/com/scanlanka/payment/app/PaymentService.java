package com.scanlanka.payment.app;

import com.scanlanka.checkout.app.StockReservationService;
import com.scanlanka.order.domain.DeliveryPayment;
import com.scanlanka.order.domain.Order;
import com.scanlanka.order.domain.OrderStatus;
import com.scanlanka.order.domain.OrderStatusEvent.ActorType;
import com.scanlanka.order.app.OrderService;
import com.scanlanka.order.infra.OrderRepository;
import com.scanlanka.payment.domain.Payment;
import com.scanlanka.payment.domain.PaymentEvent;
import com.scanlanka.payment.infra.PaymentEventRepository;
import com.scanlanka.payment.infra.PaymentRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * PayHere payments (06). PAID only on a signature-verified, amount-matched notify; **idempotent**
 * (payment_event unique) and on success **atomically decrements stock** (T-1/T-4/T-10/T-11).
 */
@Service
public class PaymentService {

    private final OrderRepository orders;
    private final OrderService orderService;
    private final PaymentRepository payments;
    private final PaymentEventRepository events;
    private final PayHereHasher hasher;
    private final PayHereProperties props;
    private final OrderFulfilmentConfirmer confirmer;
    private final StockReservationService reservations;

    public PaymentService(OrderRepository orders, OrderService orderService, PaymentRepository payments,
                          PaymentEventRepository events, PayHereHasher hasher, PayHereProperties props,
                          OrderFulfilmentConfirmer confirmer, StockReservationService reservations) {
        this.orders = orders;
        this.orderService = orderService;
        this.payments = payments;
        this.events = events;
        this.hasher = hasher;
        this.props = props;
        this.confirmer = confirmer;
        this.reservations = reservations;
    }

    public record InitiateResult(String checkoutUrl, Map<String, String> params) {}

    public record NotifyParams(String merchantId, String orderId, String amount, String currency,
                               String statusCode, String md5sig, String paymentId) {}

    @Transactional
    public InitiateResult initiate(String orderNumber) {
        Order order = orders.findByOrderNumber(orderNumber)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));
        if (order.getDeliveryPayment() == DeliveryPayment.COD) {
            // Courier orders are full COD — nothing is charged online (FR-PAY-15).
            throw new ResponseStatusException(HttpStatus.CONFLICT, "COURIER_ORDER_IS_COD");
        }
        if (order.getStatus() != OrderStatus.PENDING_PAYMENT) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "ORDER_NOT_PAYABLE");
        }
        String amount = formatAmount(order.getTotalCents());
        String hash = hasher.initHash(orderNumber, amount, props.currency());

        payments.findByOrderId(order.getId()).orElseGet(() -> payments.save(
            new Payment(order.getId(), Payment.Method.PAYHERE, Payment.Status.PENDING,
                order.getTotalCents(), props.currency())));

        Map<String, String> params = new LinkedHashMap<>();
        params.put("merchant_id", props.merchantId());
        params.put("return_url", props.returnUrl());
        params.put("cancel_url", props.cancelUrl());
        params.put("notify_url", props.notifyUrl());
        params.put("order_id", orderNumber);
        params.put("items", "Scan Lanka Order " + orderNumber);
        params.put("currency", props.currency());
        params.put("amount", amount);
        params.put("hash", hash);                 // secret never sent — only the hash
        return new InitiateResult(props.checkoutUrl(), params);
    }

    /** PayHere server-to-server notify — the ONLY proof of payment (06 FR-PAY-2/3). */
    @Transactional
    public void handleNotify(NotifyParams n) {
        // Defense-in-depth (P1-3): reject a notify addressed to a different merchant. The signature is
        // already computed with OUR merchant id + secret (see PayHereHasher), so a foreign merchant_id
        // can never pass verifyNotify — but failing fast here keeps the intent explicit and auditable.
        if (props.merchantId() != null && !props.merchantId().isBlank()
            && !props.merchantId().equals(n.merchantId())) {
            return; // wrong merchant — ignore
        }
        boolean sigOk = hasher.verifyNotify(n.orderId(), n.amount(), n.currency(), n.statusCode(), n.md5sig());
        String ref = (n.paymentId() != null && !n.paymentId().isBlank()) ? n.paymentId() : n.orderId();

        // Idempotency (T-4): if this (provider, ref, status) was already handled, no-op. Check-then-insert
        // in THIS transaction; the unique index is the race backstop. Done before any side effects so a
        // replay never re-processes. (Inserting-and-catching would mark the tx rollback-only → commit 500.)
        if (events.existsByProviderAndExternalRefAndStatusCode("PAYHERE", ref, n.statusCode())) {
            return; // already processed
        }
        events.save(new PaymentEvent("PAYHERE", ref, n.statusCode(), sigOk));
        if (!sigOk) return; // forged/invalid signature → ignore (T-11)

        Order order = orders.findByOrderNumber(n.orderId()).orElse(null);
        if (order == null) return;
        if (!formatAmount(order.getTotalCents()).equals(n.amount())
            || !props.currency().equalsIgnoreCase(n.currency())) {
            return; // amount/currency mismatch → never mark paid (T-1)
        }

        if ("2".equals(n.statusCode())) {                       // PayHere success
            String note = order.getStatus() == OrderStatus.CANCELLED
                ? "PayHere notify after auto-cancel — order revived"
                : "PayHere notify";
            if (confirmer.confirmPaidAndDecrement(order, ActorType.SYSTEM, null, note)) {
                payments.findByOrderId(order.getId()).ifPresent(p -> {
                    p.markPaid(n.paymentId(), n.statusCode());
                    payments.save(p);
                });
            }
        } else if (order.getStatus() == OrderStatus.PENDING_PAYMENT) {
            orderService.transition(order.getId(), OrderStatus.PAYMENT_FAILED, ActorType.SYSTEM, null,
                "PayHere status " + n.statusCode());
            reservations.releaseForOrder(order.getId());
        }
    }

    private static String formatAmount(long cents) {
        return String.format(Locale.US, "%.2f", cents / 100.0);
    }
}
