package com.scanlanka.checkout.app;

import com.scanlanka.checkout.domain.BoardSizeTier;
import com.scanlanka.checkout.domain.CourierRateCard;
import com.scanlanka.checkout.domain.CourierRateCardId;
import com.scanlanka.checkout.domain.CourierZone;
import com.scanlanka.checkout.domain.DeliveryMethod;
import com.scanlanka.checkout.domain.DeliveryMethodConfig;
import com.scanlanka.checkout.domain.LorryZone;
import com.scanlanka.checkout.domain.PostalZone;
import com.scanlanka.checkout.infra.CourierRateCardRepository;
import com.scanlanka.checkout.infra.DeliveryMethodConfigRepository;
import com.scanlanka.checkout.infra.DeliverySettingsRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Resolves the available delivery rails for a cart + postal code (17 FR-DELIV-1, delivery-cost-model.md).
 * Server-authoritative: postal code → lorry/courier zones, per-rail eligibility, and each rail's
 * server-computed charge via the two pure engines. No AI in the charged path. Money in LKR cents.
 *
 * <p>Default min bill if {@code delivery_settings} is somehow empty.
 */
@Service
public class DeliveryOptionsService {

    private static final long DEFAULT_MIN_BILL_CENTS = 600000; // Rs 6,000

    private final CourierRateCardRepository courierRates;
    private final DeliveryMethodConfigRepository methodConfig;
    private final DeliverySettingsRepository settings;
    private final LorryCostEngine lorryEngine;
    private final CourierEstimateEngine courierEngine;
    private final CourierZoneResolver zoneResolver;

    public DeliveryOptionsService(CourierRateCardRepository courierRates,
                                  DeliveryMethodConfigRepository methodConfig, DeliverySettingsRepository settings,
                                  LorryCostEngine lorryEngine, CourierEstimateEngine courierEngine,
                                  CourierZoneResolver zoneResolver) {
        this.courierRates = courierRates;
        this.methodConfig = methodConfig;
        this.settings = settings;
        this.lorryEngine = lorryEngine;
        this.courierEngine = courierEngine;
        this.zoneResolver = zoneResolver;
    }

    /** A cart line with its delivery attributes already resolved (variant else product). */
    public record CartLine(BoardSizeTier boardSizeTier,              // null ⇒ not couriable
                           Long lorryColomboCents, Long lorrySuburbCents, Long lorryOuterCents,
                           boolean whatsappOnly, int quantity) {}

    /** One delivery rail's outcome for the cart. */
    public record Option(DeliveryMethod method, boolean available, String reason,
                         long prepaidCents,          // lorry charge collected online (0 for courier)
                         long courierEstimateCents,  // courier approximate, COD (0 for lorry)
                         boolean someArranged) {}     // lorry: ≥1 far line priced manually by admin

    /** The whole delivery picture for a cart. {@code whatsappOnly} ⇒ no rails, route to WhatsApp (12). */
    public record DeliveryQuote(boolean whatsappOnly, boolean postalServiceable, List<Option> options) {}

    public DeliveryQuote options(List<CartLine> lines, String postalCode, String city, long subtotalCents) {
        // Any whatsapp-only item ⇒ neither rail; route the whole order to WhatsApp/quote (FR-DELIV-6d).
        if (lines.stream().anyMatch(CartLine::whatsappOnly)) {
            return new DeliveryQuote(true, false, List.of());
        }

        Optional<PostalZone> zone = zoneResolver.postalZone(normalize(postalCode));
        Optional<CourierZone> courierZone = zoneResolver.courierZone(normalize(postalCode), city);
        List<Option> options = new ArrayList<>();

        if (enabled(DeliveryMethod.COMPANY_LORRY)) {
            options.add(lorryOption(lines, subtotalCents, zone.orElse(null)));
        }
        if (enabled(DeliveryMethod.COURIER)) {
            options.add(courierOption(lines, zone.orElse(null), courierZone.orElse(null)));
        }
        return new DeliveryQuote(false, zone.isPresent(), options);
    }

    private Option lorryOption(List<CartLine> lines, long subtotalCents, PostalZone zone) {
        if (zone == null) {
            return unavailable(DeliveryMethod.COMPANY_LORRY, "NOT_SERVICEABLE_POSTAL");
        }
        LorryZone lz = zone.getLorryZone();
        List<LorryCostEngine.Line> engineLines = lines.stream()
            .map(l -> new LorryCostEngine.Line(lorryCharge(l, lz), l.quantity()))
            .toList();
        LorryCostEngine.Result result = lorryEngine.compute(engineLines, subtotalCents, minBillCents());
        if (result instanceof LorryCostEngine.Result.Available a) {
            return new Option(DeliveryMethod.COMPANY_LORRY, true, null, a.prepaidCents(), 0, a.someArranged());
        }
        return unavailable(DeliveryMethod.COMPANY_LORRY,
            ((LorryCostEngine.Result.Unavailable) result).reason());
    }

    private Option courierOption(List<CartLine> lines, PostalZone zone, CourierZone courierZone) {
        if (zone == null || courierZone == null) {
            return unavailable(DeliveryMethod.COURIER, "NOT_SERVICEABLE_POSTAL");
        }
        if (lines.stream().anyMatch(l -> l.boardSizeTier() == null)) {
            return unavailable(DeliveryMethod.COURIER, "MISSING_SIZE_TIER");
        }
        long estimate = 0;
        for (CartLine line : lines) {
            CourierRateCard rate = courierRates.findById(new CourierRateCardId(courierZone, line.boardSizeTier()))
                .orElseThrow(() -> new IllegalStateException(
                    "No courier rate for zone " + courierZone + " tier " + line.boardSizeTier()));
            estimate += courierEngine.estimateLine(rate.getFlatCents(), line.quantity());
        }
        return new Option(DeliveryMethod.COURIER, true, null, 0, estimate, false);
    }

    private static LorryCostEngine.Charge lorryCharge(CartLine line, LorryZone zone) {
        Long cell = switch (zone) {
            case COLOMBO -> line.lorryColomboCents();
            case SUBURB -> line.lorrySuburbCents();
            case OUTER -> line.lorryOuterCents();
        };
        return cell != null ? LorryCostEngine.Charge.flat(cell) : LorryCostEngine.Charge.ARRANGED;
    }

    private boolean enabled(DeliveryMethod method) {
        return methodConfig.findById(method).map(DeliveryMethodConfig::isEnabled).orElse(true);
    }

    private long minBillCents() {
        return settings.findFirstByOrderByIdAsc()
            .map(s -> s.getLorryMinBillCents()).orElse(DEFAULT_MIN_BILL_CENTS);
    }

    private static Option unavailable(DeliveryMethod method, String reason) {
        return new Option(method, false, reason, 0, 0, false);
    }

    private static String normalize(String postalCode) {
        return postalCode == null ? null : postalCode.trim();
    }
}
