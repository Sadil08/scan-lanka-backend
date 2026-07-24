package com.scanlanka.order.app.receipt;

import com.scanlanka.order.domain.Order;
import com.scanlanka.order.domain.OrderItem;
import com.scanlanka.order.domain.OrderReceipt;
import com.scanlanka.order.domain.OrderStatus;
import com.scanlanka.order.infra.OrderReceiptRepository;
import com.scanlanka.payment.domain.Payment;
import com.scanlanka.payment.infra.PaymentRepository;
import com.scanlanka.shared.storage.ImageStorage;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/** Builds receipt models, generates/stores PDFs idempotently (09 FR-18). */
@Service
public class ReceiptService {

    private static final Set<OrderStatus> RECEIPT_ELIGIBLE = EnumSet.of(
        OrderStatus.PAID, OrderStatus.CONFIRMED, OrderStatus.PACKED, OrderStatus.SHIPPED,
        OrderStatus.READY_FOR_PICKUP, OrderStatus.DELIVERY_FAILED, OrderStatus.COMPLETED);

    private final OrderReceiptRepository receipts;
    private final PaymentRepository payments;
    private final ReceiptPdfRenderer pdfRenderer;
    private final ImageStorage storage;

    public ReceiptService(OrderReceiptRepository receipts, PaymentRepository payments,
                          ReceiptPdfRenderer pdfRenderer, ImageStorage storage) {
        this.receipts = receipts;
        this.payments = payments;
        this.pdfRenderer = pdfRenderer;
        this.storage = storage;
    }

    public ReceiptModel buildModel(Order order, List<OrderItem> items) {
        boolean invoice = order.getBillName() != null && !order.getBillName().isBlank();
        List<ReceiptModel.Line> lines = items.stream()
            .map(i -> new ReceiptModel.Line(
                i.getSkuSnapshot(), i.getNameSnapshot(), spec(i),
                i.getQuantity(), i.getUnitPriceCents(), i.getLineTotalCents()))
            .toList();
        return new ReceiptModel(
            order.getOrderNumber(), order.getContactName(), order.getContactEmail(), order.getContactPhone(),
            order.getFulfilmentType().name(), order.getDeliveryMethod(), order.getDeliveryPayment().name(),
            paymentMethod(order),
            order.getShipStreet(), order.getShipCity(), order.getShipProvince(), order.getShipPostalCode(),
            order.getBillName(), order.getBillTaxId(), order.getBillStreet(),
            order.getBillCity(), order.getBillProvince(), order.getBillPostalCode(),
            order.getSubtotalCents(), order.getDeliveryCents(), order.getTaxCents(),
            order.getTotalCents(), order.getDeliveryCodCents(), order.getCourierEstimateCents(), lines, invoice);
    }

    /** Generate PDF once per order; subsequent calls return the stored bytes. */
    @Transactional
    public byte[] ensurePdf(Order order, List<OrderItem> items) {
        requireEligible(order);
        return receipts.findById(order.getId())
            .map(r -> storage.load(r.getStorageKey()))
            .orElseGet(() -> generateAndStore(order, items));
    }

    @Transactional(readOnly = true)
    public byte[] loadPdf(Order order) {
        requireEligible(order);
        OrderReceipt r = receipts.findById(order.getId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Receipt not found"));
        return storage.load(r.getStorageKey());
    }

    private byte[] generateAndStore(Order order, List<OrderItem> items) {
        byte[] pdf = pdfRenderer.render(buildModel(order, items));
        String key = storage.store(pdf, "pdf").key();
        receipts.save(new OrderReceipt(order.getId(), key));
        return pdf;
    }

    private void requireEligible(Order order) {
        if (!RECEIPT_ELIGIBLE.contains(order.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "RECEIPT_NOT_AVAILABLE");
        }
    }

    /**
     * COD orders (courier, or lorry paid cash on delivery) never create a {@link Payment} row - there is
     * no online transaction to record - so the old blanket "ONLINE" fallback was wrong for every COD
     * order. Fall back to the order's own {@code deliveryPayment} instead.
     */
    private String paymentMethod(Order order) {
        return payments.findByOrderId(order.getId())
            .map(Payment::getMethod)
            .orElse(order.getDeliveryPayment().name());
    }

    private static String spec(OrderItem item) {
        String hc = item.getHandlingClassSnapshot();
        return hc == null || hc.isBlank() ? "—" : hc;
    }
}
