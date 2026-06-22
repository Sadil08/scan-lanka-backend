package com.scanlanka.checkout.app;

import com.scanlanka.catalog.infra.ProductRepository;
import com.scanlanka.catalog.infra.ProductVariantRepository;
import com.scanlanka.checkout.domain.StockReservation;
import com.scanlanka.checkout.infra.StockReservationRepository;
import com.scanlanka.order.app.OrderCommands.LineSnapshot;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/** Soft-reserve stock at checkout (05 FR-CHECKOUT-7). */
@Service
public class StockReservationService {

    public static final Duration RESERVATION_TTL = Duration.ofMinutes(15);

    private final StockReservationRepository reservations;
    private final ProductRepository products;
    private final ProductVariantRepository variants;

    public StockReservationService(StockReservationRepository reservations,
                                   ProductRepository products,
                                   ProductVariantRepository variants) {
        this.reservations = reservations;
        this.products = products;
        this.variants = variants;
    }

    /** Available units after active reservations; null physical stock = unlimited. */
    @Transactional(readOnly = true)
    public int availableQuantity(Long productId, Long variantId, Integer physicalStock) {
        if (physicalStock == null) return Integer.MAX_VALUE;
        int reserved = reservations.sumActiveQuantity(productId, variantId, Instant.now());
        return Math.max(0, physicalStock - reserved);
    }

    @Transactional
    public void reserveForOrder(Long orderId, List<LineSnapshot> lines) {
        Instant expiresAt = Instant.now().plus(RESERVATION_TTL);
        for (LineSnapshot line : lines) {
            reserveLine(orderId, line.productId(), line.variantId(), line.quantity(), expiresAt);
        }
    }

    private void reserveLine(Long orderId, Long productId, Long variantId, int qty, Instant expiresAt) {
        Integer physical = physicalStock(productId, variantId);
        if (physical == null) return;
        // Lock product row to serialize concurrent checkouts on the last unit.
        products.findByIdForUpdate(productId).orElseThrow(() -> stockExceeded());
        int available = availableQuantity(productId, variantId, physical);
        if (available < qty) throw stockExceeded();
        reservations.save(new StockReservation(orderId, productId, variantId, qty, expiresAt));
    }

    @Transactional
    public int releaseExpired() {
        return reservations.releaseExpired(Instant.now());
    }

    /** Release holds when payment fails or is abandoned (06 FR-PAY-9). */
    @Transactional
    public void releaseForOrder(Long orderId) {
        reservations.releaseForOrder(orderId);
    }

    /** Convert soft-reserve to fulfilled stock decrement path (06 FR-PAY-5). */
    @Transactional
    public void consumeForOrder(Long orderId) {
        reservations.releaseForOrder(orderId);
    }

    private Integer physicalStock(Long productId, Long variantId) {
        if (variantId != null) {
            return variants.findById(variantId)
                .filter(v -> v.getProductId().equals(productId))
                .map(v -> v.getStockQty())
                .orElse(0);
        }
        return products.findById(productId).map(p -> p.getStockQty()).orElse(0);
    }

    private static ResponseStatusException stockExceeded() {
        return new ResponseStatusException(HttpStatus.CONFLICT, "STOCK_EXCEEDED");
    }
}
