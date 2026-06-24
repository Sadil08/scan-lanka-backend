package com.scanlanka.checkout.app;

import com.scanlanka.catalog.app.ProductLookupService;
import com.scanlanka.catalog.app.ProductLookupService.OrderLine;
import com.scanlanka.checkout.app.DeliveryCostEngine.Config;
import com.scanlanka.checkout.app.DeliveryCostEngine.Item;
import com.scanlanka.checkout.app.DeliveryCostEngine.ZoneRates;
import com.scanlanka.checkout.domain.DeliveryConfig;
import com.scanlanka.checkout.domain.DeliveryZone;
import com.scanlanka.checkout.domain.TaxConfig;
import com.scanlanka.checkout.infra.DeliveryConfigRepository;
import com.scanlanka.checkout.infra.DeliveryZonePostalCodeRepository;
import com.scanlanka.checkout.infra.DeliveryZoneRepository;
import com.scanlanka.checkout.infra.TaxConfigRepository;
import com.scanlanka.order.app.OrderCommands;
import com.scanlanka.order.app.OrderCommands.CreateOrderCommand;
import com.scanlanka.order.app.OrderCommands.LineSnapshot;
import com.scanlanka.order.app.OrderService;
import com.scanlanka.order.domain.DeliveryPayment;
import com.scanlanka.order.domain.FulfilmentType;
import com.scanlanka.order.domain.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * Checkout (05-checkout-delivery). Server recomputes EVERY total (SEC-PAY): line prices (capped to
 * stock), delivery (rule engine), tax. Charged amount is always LKR. place() creates the order draft.
 */
@Service
public class CheckoutService {

    private final ProductLookupService catalog;
    private final DeliveryZoneRepository zones;
    private final DeliveryZonePostalCodeRepository postalCodes;
    private final DeliveryConfigRepository deliveryConfigs;
    private final TaxConfigRepository taxConfigs;
    private final DeliveryCostEngine deliveryEngine;
    private final OrderService orderService;
    private final StockReservationService reservations;

    public CheckoutService(ProductLookupService catalog, DeliveryZoneRepository zones,
                           DeliveryZonePostalCodeRepository postalCodes, DeliveryConfigRepository deliveryConfigs,
                           TaxConfigRepository taxConfigs, DeliveryCostEngine deliveryEngine, OrderService orderService,
                           StockReservationService reservations) {
        this.catalog = catalog;
        this.zones = zones;
        this.postalCodes = postalCodes;
        this.deliveryConfigs = deliveryConfigs;
        this.taxConfigs = taxConfigs;
        this.deliveryEngine = deliveryEngine;
        this.orderService = orderService;
        this.reservations = reservations;
    }

    public record ItemInput(Long productId, Long variantId, int quantity) {}

    public record Quote(long subtotalCents, long deliveryCents, long taxCents, long totalCents,
                        long deliveryCodCents, boolean serviceable, int lineCount) {}

    public record PlaceInput(List<ItemInput> items, FulfilmentType fulfilmentType, DeliveryPayment deliveryPayment,
                             OrderCommands.Address ship, OrderCommands.Billing billing,
                             String contactName, String contactPhone, String contactEmail,
                             Long customerId, String guestEmail) {}

    public record Placed(String orderNumber, long totalCents) {}

    @Transactional(readOnly = true)
    public Quote quote(List<ItemInput> items, FulfilmentType fulfilmentType, String postalCode,
                       DeliveryPayment deliveryPayment) {
        return computeQuote(items, fulfilmentType, postalCode, deliveryPayment, false).quote();
    }

    /** A priced, stock-capped, deduped line plus the quote derived from a single pricing pass. */
    private record PricedLine(OrderLine line, long unitPriceCents, int quantity, long lineTotalCents) {}
    private record QuoteResult(Quote quote, List<PricedLine> lines) {}

    /**
     * Resolve + price every line exactly ONCE (so totals and the order snapshot can never disagree),
     * after consolidating repeated (product, variant) inputs into a single line. Every total is
     * server-computed.
     */
    private QuoteResult computeQuote(List<ItemInput> items, FulfilmentType fulfilmentType, String postalCode,
                                     DeliveryPayment deliveryPayment, boolean strict) {
        List<PricedLine> priced = priceLines(items, strict);
        long subtotal = priced.stream().mapToLong(PricedLine::lineTotalCents).sum();

        boolean serviceable = true;
        long delivery = 0;
        if (fulfilmentType == FulfilmentType.DELIVERY) {
            ZoneRates zone = resolveZone(postalCode).orElse(null);
            if (zone == null) {
                serviceable = false;                            // not deliverable → block at place (FR-3b)
            } else {
                delivery = deliveryEngine.compute(zone, loadDeliveryConfig(), deliveryItems(priced));
            }
        }

        long tax = Math.round(subtotal * (loadTaxRateBps() / 10000.0));
        long deliveryCod = 0;
        long total;
        if (deliveryPayment == DeliveryPayment.COD) {           // delivery paid on delivery (06 FR-PAY-7)
            total = subtotal + tax;
            deliveryCod = delivery;
        } else {
            total = subtotal + delivery + tax;
        }
        Quote quote = new Quote(subtotal, delivery, tax, total, deliveryCod, serviceable, priced.size());
        return new QuoteResult(quote, priced);
    }

    @Transactional
    public Placed place(PlaceInput in) {
        if (in.items() == null || in.items().isEmpty()) throw badRequest("EMPTY_CART");
        String postal = in.ship() != null ? in.ship().postalCode() : null;
        // strict=true: a resolvable product that can't satisfy the requested qty (e.g. its last unit is
        // already reserved) is a hard 409, not a silently-dropped line (FR-CHECKOUT-7).
        QuoteResult qr = computeQuote(in.items(), in.fulfilmentType(), postal, in.deliveryPayment(), true);
        Quote q = qr.quote();
        if (q.lineCount() == 0) throw badRequest("NO_AVAILABLE_ITEMS");
        if (in.fulfilmentType() == FulfilmentType.DELIVERY && !q.serviceable()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "NOT_SERVICEABLE");
        }

        List<LineSnapshot> snapshots = qr.lines().stream()
            .map(p -> new LineSnapshot(p.line().productId(), p.line().variantId(), p.line().sku(),
                p.line().name(), p.line().handlingClass(), p.unitPriceCents(), p.quantity(), p.lineTotalCents()))
            .toList();

        CreateOrderCommand cmd = new CreateOrderCommand(
            in.customerId(), in.guestEmail(),
            in.contactName(), in.contactPhone(), in.contactEmail(),
            in.fulfilmentType(), in.ship(), in.billing(), snapshots,
            q.subtotalCents(), 0, q.deliveryCents(), q.taxCents(), q.totalCents(),
            in.deliveryPayment(), q.deliveryCodCents());
        Order order = orderService.createDraft(cmd);
        reservations.reserveForOrder(order.getId(), snapshots);
        return new Placed(order.getOrderNumber(), q.totalCents());
    }

    // --- helpers ---

    private record LineKey(Long productId, Long variantId) {}

    /**
     * Consolidate repeated (product, variant) inputs, resolve each against the catalog, cap to
     * available stock, and price — a single resolution/pricing pass. Insertion order is preserved.
     * Lines with no resolvable product or non-positive quantity are dropped.
     *
     * <p>When {@code strict} (placement), a resolvable product that can't satisfy its requested qty
     * (e.g. its last unit is already reserved) is a hard 409 rather than a silently-capped/dropped
     * line; the lenient quote path just caps for display (FR-CHECKOUT-7).
     */
    private List<PricedLine> priceLines(List<ItemInput> items, boolean strict) {
        LinkedHashMap<LineKey, Integer> requested = new LinkedHashMap<>();
        for (ItemInput it : items) {
            if (it.productId() == null || it.quantity() < 1) continue;
            requested.merge(new LineKey(it.productId(), it.variantId()), it.quantity(), Integer::sum);
        }
        List<PricedLine> out = new ArrayList<>();
        for (var entry : requested.entrySet()) {
            LineKey key = entry.getKey();
            OrderLine l = catalog.resolveOrderLine(key.productId(), key.variantId()).orElse(null);
            if (l == null) continue;
            int available = reservations.availableQuantity(l.productId(), l.variantId(), l.stock());
            if (strict && available < entry.getValue()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "STOCK_EXCEEDED");
            }
            int qty = available == Integer.MAX_VALUE ? entry.getValue() : Math.min(entry.getValue(), available);
            if (qty < 1) continue;
            out.add(new PricedLine(l, l.unitPriceCents(), qty, l.unitPriceCents() * (long) qty));
        }
        return out;
    }

    private List<Item> deliveryItems(List<PricedLine> priced) {
        List<Item> items = new ArrayList<>();
        for (PricedLine p : priced) {
            // weight 0 until D-8 weights are supplied; handling class drives surcharges now
            items.add(new Item(p.line().handlingClass(), p.quantity(), 0));
        }
        return items;
    }

    private java.util.Optional<ZoneRates> resolveZone(String postalCode) {
        if (postalCode == null) return java.util.Optional.empty();
        return postalCodes.findById(postalCode)
            .flatMap(pc -> zones.findById(pc.getZoneId()))
            .filter(DeliveryZone::isActive)
            .map(z -> new ZoneRates(z.getBaseChargeCents(), z.getPerKgChargeCents(), z.getFuelPct().doubleValue()));
    }

    private Config loadDeliveryConfig() {
        DeliveryConfig c = deliveryConfigs.findById(1)
            .orElseThrow(() -> new IllegalStateException("delivery_config missing"));
        return new Config(c.getPickFirstCents(), c.getPickNextCents(),
            c.getFragileSurchargeCents(), c.getOversizeSurchargeCents());
    }

    private int loadTaxRateBps() {
        return taxConfigs.findById(1).map(TaxConfig::getRateBps).orElse(0);
    }

    private static ResponseStatusException badRequest(String code) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, code);
    }
}
