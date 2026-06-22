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
        List<OrderLine> resolved = resolveLines(items);
        List<long[]> priced = priceLines(items, resolved);     // [unitPrice, qty, lineTotal] per available line
        long subtotal = priced.stream().mapToLong(p -> p[2]).sum();

        boolean serviceable = true;
        long delivery = 0;
        if (fulfilmentType == FulfilmentType.DELIVERY) {
            ZoneRates zone = resolveZone(postalCode).orElse(null);
            if (zone == null) {
                serviceable = false;                            // not deliverable → block at place (FR-3b)
            } else {
                delivery = deliveryEngine.compute(zone, loadDeliveryConfig(), deliveryItems(resolved, priced));
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
        return new Quote(subtotal, delivery, tax, total, deliveryCod, serviceable, priced.size());
    }

    @Transactional
    public Placed place(PlaceInput in) {
        if (in.items() == null || in.items().isEmpty()) throw badRequest("EMPTY_CART");
        String postal = in.ship() != null ? in.ship().postalCode() : null;
        Quote q = quote(in.items(), in.fulfilmentType(), postal, in.deliveryPayment());
        if (q.lineCount() == 0) throw badRequest("NO_AVAILABLE_ITEMS");
        if (in.fulfilmentType() == FulfilmentType.DELIVERY && !q.serviceable()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "NOT_SERVICEABLE");
        }

        List<OrderLine> resolved = resolveLines(in.items());
        List<long[]> priced = priceLines(in.items(), resolved);
        List<LineSnapshot> snapshots = new ArrayList<>();
        for (int i = 0; i < resolved.size(); i++) {
            OrderLine l = resolved.get(i);
            long[] p = priced.get(i);
            snapshots.add(new LineSnapshot(l.productId(), l.variantId(), l.sku(), l.name(),
                l.handlingClass(), p[0], (int) p[1], p[2]));
        }

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

    private List<OrderLine> resolveLines(List<ItemInput> items) {
        List<OrderLine> out = new ArrayList<>();
        for (ItemInput it : items) {
            catalog.resolveOrderLine(it.productId(), it.variantId()).ifPresent(out::add);
        }
        return out;
    }

    /** Returns [unitPrice, cappedQty, lineTotal] per resolved (available) line, aligned to resolveLines order. */
    private List<long[]> priceLines(List<ItemInput> items, List<OrderLine> resolved) {
        List<long[]> out = new ArrayList<>();
        for (OrderLine l : resolved) {
            int requested = items.stream()
                .filter(i -> i.productId().equals(l.productId())
                    && java.util.Objects.equals(i.variantId(), l.variantId()))
                .mapToInt(ItemInput::quantity).findFirst().orElse(1);
            int available = reservations.availableQuantity(l.productId(), l.variantId(), l.stock());
            int qty = available == Integer.MAX_VALUE ? requested : Math.min(requested, available);
            if (qty < 1) continue;
            out.add(new long[] { l.unitPriceCents(), qty, l.unitPriceCents() * (long) qty });
        }
        return out;
    }

    private List<Item> deliveryItems(List<OrderLine> resolved, List<long[]> priced) {
        List<Item> items = new ArrayList<>();
        for (int i = 0; i < priced.size(); i++) {
            // weight 0 until D-8 weights are supplied; handling class drives surcharges now
            items.add(new Item(resolved.get(i).handlingClass(), (int) priced.get(i)[1], 0));
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
