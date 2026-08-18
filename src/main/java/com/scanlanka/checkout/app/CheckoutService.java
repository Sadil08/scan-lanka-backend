package com.scanlanka.checkout.app;

import com.scanlanka.catalog.app.ProductLookupService;
import com.scanlanka.catalog.app.ProductLookupService.OrderLine;
import com.scanlanka.checkout.app.DeliveryOptionsService.CartLine;
import com.scanlanka.checkout.app.DeliveryOptionsService.DeliveryQuote;
import com.scanlanka.checkout.app.DeliveryOptionsService.Option;
import com.scanlanka.checkout.domain.DeliveryMethod;
import com.scanlanka.checkout.domain.PayHereFeeConfig;
import com.scanlanka.checkout.domain.PaymentChoice;
import com.scanlanka.checkout.domain.TaxConfig;
import com.scanlanka.checkout.infra.PayHereFeeConfigRepository;
import com.scanlanka.checkout.infra.TaxConfigRepository;
import com.scanlanka.order.app.OrderCommands;
import com.scanlanka.order.app.OrderCommands.CreateOrderCommand;
import com.scanlanka.order.app.OrderCommands.LineSnapshot;
import com.scanlanka.order.app.OrderPlacedEvent;
import com.scanlanka.order.app.OrderService;
import org.springframework.context.ApplicationEventPublisher;
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
 * Checkout (05-checkout-delivery, 17 two-rail model). Server recomputes EVERY total (SEC-PAY): line
 * prices (capped to stock), delivery (per the chosen rail), tax. Every order is delivered — no pickup.
 *
 * <p><b>COMPANY_LORRY</b> → product + fixed lorry charge prepaid online (PREPAID).
 * <b>COURIER</b> → full COD: nothing charged online; an approximate total (product + tax + weight estimate)
 * is shown and collected on delivery. Money in integer LKR cents.
 */
@Service
public class CheckoutService {

    private final ProductLookupService catalog;
    private final TaxConfigRepository taxConfigs;
    private final PayHereFeeConfigRepository payHereFeeConfigs;
    private final DeliveryOptionsService deliveryOptions;
    private final OrderService orderService;
    private final StockReservationService reservations;
    private final ApplicationEventPublisher events;

    public CheckoutService(ProductLookupService catalog, TaxConfigRepository taxConfigs,
                           PayHereFeeConfigRepository payHereFeeConfigs,
                           DeliveryOptionsService deliveryOptions, OrderService orderService,
                           StockReservationService reservations, ApplicationEventPublisher events) {
        this.catalog = catalog;
        this.taxConfigs = taxConfigs;
        this.payHereFeeConfigs = payHereFeeConfigs;
        this.deliveryOptions = deliveryOptions;
        this.orderService = orderService;
        this.reservations = reservations;
        this.events = events;
    }

    public record ItemInput(Long productId, Long variantId, int quantity) {}

    /**
     * @param onlineTotalCents  what is charged online before any card surcharge (lorry: subtotal+delivery+tax;
     *                          courier: 0) — also the door total collected on COD
     * @param payHereFeeCents   PayHere's card surcharge on top of onlineTotalCents, charged only when the
     *                          customer pays by CARD (never for bank transfer, COD, or courier); admin-tunable
     * @param approxTotalCents  what the customer ultimately pays (courier: + estimate, paid on delivery)
     * @param available         whether the chosen rail is offered; {@code reason} explains why not
     */
    public record Quote(long subtotalCents, long deliveryCents, long taxCents, long onlineTotalCents,
                        long payHereFeeCents, long courierEstimateCents, long approxTotalCents, boolean someArranged,
                        DeliveryMethod deliveryMethod, boolean available, String reason, int lineCount) {}

    public record PlaceInput(List<ItemInput> items, DeliveryMethod deliveryMethod, PaymentChoice paymentChoice,
                             OrderCommands.Address ship, OrderCommands.Billing billing,
                             String contactName, String contactPhone, String contactEmail,
                             Long customerId, String guestEmail, String paymentMethod) {}

    public record Placed(String orderNumber, long onlineTotalCents) {}

    /** @param paymentMethod CARD | BANK — only CARD (PayHere) ever carries a surcharge; null/other ⇒ none shown */
    @Transactional(readOnly = true)
    public Quote quote(List<ItemInput> items, DeliveryMethod deliveryMethod, String postalCode, String city,
                       String paymentMethod) {
        return computeQuote(items, deliveryMethod, postalCode, city, false, paymentMethod).quote();
    }

    /** All rails for a cart + postal code (powers the checkout rail picker, 17 FR-DELIV-1). */
    @Transactional(readOnly = true)
    public DeliveryQuote deliveryOptions(List<ItemInput> items, String postalCode, String city) {
        List<PricedLine> priced = priceLines(items, false);
        long subtotal = priced.stream().mapToLong(PricedLine::lineTotalCents).sum();
        return deliveryOptions.options(cartLines(priced), postalCode, city, subtotal);
    }

    /** A priced, stock-capped, deduped line plus the quote derived from a single pricing pass. */
    private record PricedLine(OrderLine line, long unitPriceCents, int quantity, long lineTotalCents) {}
    private record QuoteResult(Quote quote, List<PricedLine> lines) {}

    /**
     * Resolve + price every line exactly ONCE (so totals and the order snapshot can never disagree),
     * after consolidating repeated (product, variant) inputs into a single line, then quote the chosen rail.
     */
    private QuoteResult computeQuote(List<ItemInput> items, DeliveryMethod method, String postalCode, String city,
                                     boolean strict, String paymentMethod) {
        List<PricedLine> priced = priceLines(items, strict);
        long subtotal = priced.stream().mapToLong(PricedLine::lineTotalCents).sum();
        long tax = Math.round(subtotal * (loadTaxRateBps() / 10000.0));

        DeliveryQuote dq = deliveryOptions.options(cartLines(priced), postalCode, city, subtotal);
        Option option = dq.options().stream().filter(o -> o.method() == method).findFirst().orElse(null);

        if (dq.whatsappOnly()) {
            return result(priced, subtotal, 0, tax, 0, 0, 0, subtotal + tax, false, method, false, "WHATSAPP_ONLY");
        }
        if (option == null) {
            return result(priced, subtotal, 0, tax, 0, 0, 0, subtotal + tax, false, method, false, "METHOD_DISABLED");
        }
        if (!option.available()) {
            return result(priced, subtotal, 0, tax, 0, 0, 0, subtotal + tax, false, method, false, option.reason());
        }

        if (method == DeliveryMethod.COMPANY_LORRY) {
            long delivery = option.prepaidCents();
            long online = subtotal + delivery + tax;          // prepaid online with the product, before any card fee
            long fee = payHereFeeCents(online, paymentMethod);
            return result(priced, subtotal, delivery, tax, fee, 0, online, online, option.someArranged(),
                method, true, null);
        }
        // COURIER — full COD: nothing online, ever; no PayHere fee applies. Approx total = product + tax + estimate.
        long estimate = option.courierEstimateCents();
        return result(priced, subtotal, 0, tax, 0, estimate, 0, subtotal + tax + estimate, false, method, true, null);
    }

    private QuoteResult result(List<PricedLine> priced, long subtotal, long delivery, long tax, long fee,
                               long estimate, long online, long approx, boolean someArranged,
                               DeliveryMethod method, boolean available, String reason) {
        Quote q = new Quote(subtotal, delivery, tax, online, fee, estimate, approx, someArranged,
            method, available, reason, priced.size());
        return new QuoteResult(q, priced);
    }

    /** 0 unless paying by CARD (PayHere) — bank transfer, COD, and courier never carry the surcharge. */
    private long payHereFeeCents(long doorTotal, String paymentMethod) {
        if (!"CARD".equalsIgnoreCase(paymentMethod)) return 0;
        int bps = payHereFeeConfigs.findFirstByOrderByIdAsc().map(PayHereFeeConfig::getRateBps).orElse(0);
        return Math.round(doorTotal * (bps / 10000.0));
    }

    @Transactional
    public Placed place(PlaceInput in) {
        if (in.items() == null || in.items().isEmpty()) throw badRequest("EMPTY_CART");
        String postal = in.ship() != null ? in.ship().postalCode() : null;
        String city = in.ship() != null ? in.ship().city() : null;
        // strict=true: a resolvable product that can't satisfy the requested qty (its last unit already
        // reserved) is a hard 409, not a silently-dropped line (FR-CHECKOUT-7).
        QuoteResult qr = computeQuote(in.items(), in.deliveryMethod(), postal, city, true, null);
        Quote q = qr.quote();
        if (q.lineCount() == 0) throw badRequest("NO_AVAILABLE_ITEMS");
        if (!q.available()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, q.reason());
        }

        List<LineSnapshot> snapshots = qr.lines().stream()
            .map(p -> new LineSnapshot(p.line().productId(), p.line().variantId(), p.line().sku(),
                p.line().name(), p.line().handlingClass(), p.unitPriceCents(), p.quantity(), p.lineTotalCents()))
            .toList();

        boolean lorry = in.deliveryMethod() == DeliveryMethod.COMPANY_LORRY;
        PaymentChoice choice = in.paymentChoice();   // null ⇒ unspecified (lorry→online, courier→COD)
        // Courier is always full COD; an EXPLICIT online choice on a courier order is rejected (SEC-DELIV-1).
        if (!lorry && choice == PaymentChoice.ONLINE) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "COURIER_IS_COD_ONLY");
        }
        // Lorry-COD (owner 2026-07-03) confirms like courier: nothing online, driver collects the full
        // door total (subtotal + lorry charge + tax). Lorry defaults to prepaid online unless COD is chosen.
        boolean cod = !lorry || choice == PaymentChoice.COD;
        DeliveryPayment payment = cod ? DeliveryPayment.COD : DeliveryPayment.PREPAID;
        long doorTotal = q.subtotalCents() + q.deliveryCents() + q.taxCents();
        long codCents = (lorry && cod) ? doorTotal : 0;   // cash the driver collects (0 for courier) — never a card fee

        // CARD | BANK for prepaid online; null for COD (nothing charged online).
        String paymentMethod = cod ? null : normalizePaymentMethod(in.paymentMethod());
        // The PayHere surcharge (admin-tunable) applies only to prepaid CARD checkouts, on the full
        // door total including delivery — never to bank transfer or any COD order (SEC-PAY: server-computed).
        long payhereFee = cod ? 0 : payHereFeeCents(doorTotal, paymentMethod);
        long onlineTotal = cod ? 0 : doorTotal + payhereFee;

        CreateOrderCommand cmd = new CreateOrderCommand(
            in.customerId(), in.guestEmail(),
            in.contactName(), in.contactPhone(), in.contactEmail(),
            FulfilmentType.DELIVERY, in.ship(), in.billing(), snapshots,
            q.subtotalCents(), 0, q.deliveryCents(), q.taxCents(), payhereFee, onlineTotal,
            payment, codCents,
            in.deliveryMethod().name(), q.courierEstimateCents(), q.someArranged(),
            paymentMethod);
        Order order = orderService.createDraft(cmd);
        reservations.reserveForOrder(order.getId(), snapshots);
        // Synchronous, in-transaction: any COD order (courier OR lorry-COD) is confirmed + stock-decremented
        // now by a payment-side listener; a lorry-online order waits for PayHere/bank (06). Also the 19 hook.
        events.publishEvent(new OrderPlacedEvent(order.getId()));
        return new Placed(order.getOrderNumber(), onlineTotal);
    }

    // --- helpers ---

    private record LineKey(Long productId, Long variantId) {}

    /**
     * Consolidate repeated (product, variant) inputs, resolve against the catalog, cap to available stock,
     * and price — a single resolution/pricing pass. Insertion order preserved. Lines with no resolvable
     * product or non-positive quantity are dropped. When {@code strict} (placement), a resolvable product
     * that can't satisfy its requested qty is a hard 409 (FR-CHECKOUT-7).
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

    private List<CartLine> cartLines(List<PricedLine> priced) {
        return priced.stream()
            .map(p -> new CartLine(p.line().name(), p.line().boardSizeTier(), p.line().weightKg(),
                p.line().lorryColomboCents(), p.line().lorrySuburbCents(), p.line().lorryOuterCents(),
                p.line().lorryColomboGateCents(), p.line().lorrySuburbGateCents(), p.line().lorryOuterGateCents(),
                p.line().lorryColomboEnabled(), p.line().lorrySuburbEnabled(), p.line().lorryOuterEnabled(),
                p.line().lorryOuterWhatsapp(), p.line().courierOuterBlocked(), p.line().courierEnabled(),
                p.line().whatsappOnly(), p.quantity()))
            .toList();
    }

    private int loadTaxRateBps() {
        return taxConfigs.findFirstByOrderByIdAsc().map(TaxConfig::getRateBps).orElse(0);
    }

    /** CARD | BANK for prepaid; blank/null defaults to CARD (PayHere) so older clients still work. */
    private static String normalizePaymentMethod(String raw) {
        if (raw == null || raw.isBlank()) return "CARD";
        String v = raw.trim().toUpperCase();
        if ("CARD".equals(v) || "BANK".equals(v)) return v;
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_PAYMENT_METHOD");
    }

    private static ResponseStatusException badRequest(String code) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, code);
    }
}
