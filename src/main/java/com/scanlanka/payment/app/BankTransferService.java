package com.scanlanka.payment.app;

import com.scanlanka.catalog.app.ImageProcessing;
import com.scanlanka.order.app.OrderService;
import com.scanlanka.order.domain.DeliveryPayment;
import com.scanlanka.order.domain.Order;
import com.scanlanka.order.domain.OrderStatus;
import com.scanlanka.order.domain.OrderStatusEvent.ActorType;
import com.scanlanka.order.infra.OrderRepository;
import com.scanlanka.payment.domain.BankTransferSlip;
import com.scanlanka.payment.domain.Payment;
import com.scanlanka.payment.infra.BankTransferSlipRepository;
import com.scanlanka.payment.infra.PaymentRepository;
import com.scanlanka.shared.security.Hashing;
import com.scanlanka.shared.storage.ImageStorage;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Bank-transfer payments (06 FR-PAY-6, T-1b). Customer uploads a slip (hardened, hashed) → order
 * AWAITING_BANK_CONFIRMATION; admin MANUALLY confirms (never auto) → PAID + atomic decrement.
 */
@Service
public class BankTransferService {

    private final OrderRepository orders;
    private final PaymentRepository payments;
    private final BankTransferSlipRepository slips;
    private final ImageProcessing imageProcessing;
    private final ImageStorage imageStorage;
    private final OrderService orderService;
    private final OrderFulfilmentConfirmer confirmer;

    public BankTransferService(OrderRepository orders, PaymentRepository payments,
                               BankTransferSlipRepository slips, ImageProcessing imageProcessing,
                               ImageStorage imageStorage, OrderService orderService,
                               OrderFulfilmentConfirmer confirmer) {
        this.orders = orders;
        this.payments = payments;
        this.slips = slips;
        this.imageProcessing = imageProcessing;
        this.imageStorage = imageStorage;
        this.orderService = orderService;
        this.confirmer = confirmer;
    }

    @Transactional
    public void uploadSlip(String orderNumber, byte[] fileBytes, Long userId, String guestEmail) {
        Order order = orders.findByOrderNumber(orderNumber)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));
        assertOwnership(order, userId, guestEmail);
        if (order.getDeliveryPayment() == DeliveryPayment.COD) {
            // Bank transfer is for COMPANY_LORRY only; courier orders are full COD (FR-PAY-6/15).
            throw new ResponseStatusException(HttpStatus.CONFLICT, "COURIER_ORDER_IS_COD");
        }
        if (order.getStatus() != OrderStatus.PENDING_PAYMENT
            && order.getStatus() != OrderStatus.BANK_SLIP_REJECTED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "ORDER_NOT_AWAITING_SLIP");
        }
        byte[] png = imageProcessing.validateAndReencode(fileBytes);        // hardened (T-6)
        String hash = Hashing.sha256Hex(fileBytes);                         // duplicate-slip flag (T-1b)
        ImageStorage.StoredImage stored = imageStorage.store(png, imageProcessing.outputExtension());

        Payment payment = payments.findByOrderId(order.getId()).orElseGet(() -> payments.save(
            new Payment(order.getId(), Payment.Method.BANK_TRANSFER,
                Payment.Status.AWAITING_BANK_CONFIRMATION, order.getTotalCents(), "LKR")));
        payment.setStatus(Payment.Status.AWAITING_BANK_CONFIRMATION);
        payments.save(payment);

        slips.save(new BankTransferSlip(payment.getId(), stored.key(), stored.url(), hash));
        if (order.getStatus() != OrderStatus.AWAITING_BANK_CONFIRMATION) {
            orderService.transition(order.getId(), OrderStatus.AWAITING_BANK_CONFIRMATION,
                ActorType.CUSTOMER, order.getCustomerId(), "bank slip uploaded");
        }
    }

    /**
     * The caller must own the order. Registered orders match on userId; guest orders match on the
     * email the order was placed with. Non-owners get 404 (no existence oracle, IDOR convention).
     */
    private void assertOwnership(Order order, Long userId, String guestEmail) {
        boolean owns;
        if (order.getCustomerId() != null) {
            owns = userId != null && userId.equals(order.getCustomerId());
        } else {
            owns = guestEmail != null && order.getContactEmail() != null
                && guestEmail.trim().equalsIgnoreCase(order.getContactEmail());
        }
        if (!owns) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found");
    }

    /** Admin-only manual confirmation — NEVER automatic (T-1b). */
    @Transactional
    public void confirm(String orderNumber, Long adminId, String note) {
        Order order = orders.findByOrderNumber(orderNumber)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));
        boolean confirmed = confirmer.confirmPaidAndDecrement(order, ActorType.ADMIN, adminId,
            "bank transfer confirmed" + (note != null ? ": " + note : ""));
        if (!confirmed) throw new ResponseStatusException(HttpStatus.CONFLICT, "NOT_AWAITING_CONFIRMATION");
        payments.findByOrderId(order.getId()).ifPresent(p -> {
            p.markPaid(null, "BANK");
            payments.save(p);
            slips.findFirstByPaymentIdOrderByUploadedAtDesc(p.getId())
                .ifPresent(s -> { s.review(BankTransferSlip.Review.CONFIRMED, adminId, note); slips.save(s); });
        });
    }

    @Transactional
    public void reject(String orderNumber, Long adminId, String note) {
        Order order = orders.findByOrderNumber(orderNumber)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));
        orderService.transition(order.getId(), OrderStatus.BANK_SLIP_REJECTED, ActorType.ADMIN, adminId,
            "bank slip rejected" + (note != null ? ": " + note : ""));
        payments.findByOrderId(order.getId()).ifPresent(p -> {
            p.setStatus(Payment.Status.BANK_REJECTED);
            payments.save(p);
            slips.findFirstByPaymentIdOrderByUploadedAtDesc(p.getId())
                .ifPresent(s -> { s.review(BankTransferSlip.Review.REJECTED, adminId, note); slips.save(s); });
        });
    }
}
