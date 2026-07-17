package com.scanlanka.checkout.app;

import com.scanlanka.checkout.domain.BoardSizeTier;
import com.scanlanka.checkout.domain.CourierRateCard;
import com.scanlanka.checkout.domain.CourierZone;
import com.scanlanka.checkout.domain.DeliveryMethod;
import com.scanlanka.checkout.domain.DeliveryMethodConfig;
import com.scanlanka.checkout.domain.DeliverySettings;
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
 * Resolves the available delivery rails for a cart + postal code (17 FR-DELIV-1, delivery-cost-model.md,
 * owner 2026-07-03: Domex-only courier). Server-authoritative: postal code → lorry/courier zones,
 * per-rail eligibility, and each rail's server-computed charge via the two pure engines. No AI in the
 * charged path. Money in LKR cents.
 *
 * <p><b>Rails (owner 2026-07-03):</b> courier is offered for <b>every couriable product in every area</b>
 * (priced by the Domex size tier). Large boards are only restricted <b>to outer areas</b>: an item flagged
 * {@code courierOuterBlocked} (6×3/6×4/8×4) hides courier when the Domex area is outer (OUTSTATION/FARAWAY)
 * with reason {@code OVERSIZE_OUTER}; an item flagged {@code lorryOuterWhatsapp} (glass, key holders, 6×4/8×4)
 * hides the lorry for an outer lorry zone (reason {@code WHATSAPP_OUTER} → contact us). Both list the
 * blocking items so the customer can remove them, switch rail, or contact us.
 */
@Service
public class DeliveryOptionsService {

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
    public record CartLine(String name,
                           BoardSizeTier boardSizeTier,              // null ⇒ not couriable (e.g. glass — lorry only)
                           java.math.BigDecimal weightKg,            // drives the Domex weight rate (null ⇒ 1 kg)
                           Long lorryColomboCents, Long lorrySuburbCents, Long lorryOuterCents,
                           Long lorryColomboGateCents, Long lorrySuburbGateCents, Long lorryOuterGateCents,
                           boolean lorryColomboEnabled, boolean lorrySuburbEnabled, boolean lorryOuterEnabled,
                           boolean lorryOuterWhatsapp, boolean courierOuterBlocked, boolean courierEnabled,
                           boolean whatsappOnly, int quantity) {}

    /** One delivery rail's outcome for the cart. */
    public record Option(DeliveryMethod method, boolean available, String reason,
                         long prepaidCents,          // lorry charge collected online/COD (0 for courier)
                         long courierEstimateCents,  // courier approximate, COD (0 for lorry)
                         boolean someArranged,       // lorry: ≥1 far line arranged manually by admin
                         long addMoreCents,          // MIN_BILL: how much more to unlock the lorry (else 0)
                         List<String> blockingItems) {} // OVERSIZE: item names that can't be couriered

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
            options.add(courierOption(lines, courierZone.orElse(null)));
        }
        return new DeliveryQuote(false, zone.isPresent(), options);
    }

    private Option lorryOption(List<CartLine> lines, long subtotalCents, PostalZone zone) {
        if (zone == null) {
            return unavailable(DeliveryMethod.COMPANY_LORRY, "NOT_SERVICEABLE_POSTAL");
        }
        LorryZone lz = zone.getLorryZone();
        // Outer WhatsApp items (glass boards, key holders, big boards, outer gate cells) hide the lorry
        // for outer addresses — outer delivery is arranged separately via contact (FR-DELIV-6d).
        if (lz == LorryZone.OUTER && lines.stream().anyMatch(CartLine::lorryOuterWhatsapp)) {
            return unavailable(DeliveryMethod.COMPANY_LORRY, "WHATSAPP_OUTER");
        }
        // Admin switched the lorry off for this zone on some item (owner 2026-07-05, e.g. small boards
        // to outer) — rail not offered; the customer simply uses the courier. Names surfaced for the UI.
        List<String> off = lines.stream()
            .filter(l -> !lorryEnabled(l, lz)).map(CartLine::name).toList();
        if (!off.isEmpty()) {
            return new Option(DeliveryMethod.COMPANY_LORRY, false, "UNAVAILABLE_ITEMS", 0, 0, false, 0, off);
        }
        long lorryCap = lorryCapCents(lz);
        List<LorryCostEngine.Line> engineLines = lines.stream()
            .map(l -> new LorryCostEngine.Line(lorryCharge(l, lz), l.quantity()))
            .toList();
        LorryCostEngine.Result result = lorryEngine.compute(engineLines, subtotalCents, lorryCap);
        if (result instanceof LorryCostEngine.Result.Available a) {
            long prepaid = capLorryTotal(lz, a.prepaidCents());
            return new Option(DeliveryMethod.COMPANY_LORRY, true, null, prepaid, 0,
                a.someArranged(), 0, List.of());
        }
        LorryCostEngine.Result.Unavailable u = (LorryCostEngine.Result.Unavailable) result;
        long addMore = u.detailCents() > 0 ? Math.max(0, u.detailCents() - subtotalCents) : 0;
        return new Option(DeliveryMethod.COMPANY_LORRY, false, u.reason(), 0, 0, false, addMore, List.of());
    }

    /**
     * Owner 2026-07-07 (unconditional as of the same-day follow-up): the WHOLE Colombo/Suburb lorry
     * total is always capped — "buy as much as you like, delivery never exceeds Rs 1,000 (Colombo) /
     * Rs 1,500 (Suburb)", on any order size. Never bumped UP if the raw sum is already lower. Outer
     * has no cap.
     */
    private long capLorryTotal(LorryZone zone, long prepaidCents) {
        if (zone == LorryZone.OUTER) return prepaidCents;
        DeliverySettings s = settings.findFirstByOrderByIdAsc().orElse(null);
        if (s == null) return prepaidCents;
        long cap = zone == LorryZone.COLOMBO ? s.getLorryCapColomboCents() : s.getLorryCapSuburbCents();
        return Math.min(prepaidCents, cap);
    }

    private Option courierOption(List<CartLine> lines, CourierZone courierZone) {
        if (courierZone == null) {
            return unavailable(DeliveryMethod.COURIER, "NOT_SERVICEABLE_POSTAL");
        }
        // Per-item courier switch (V48/V49, owner 2026-07-16: at launch only Scan White Board 1x1..4x2
        // keep the courier) — a switched-off item hides the rail; names surfaced for the UI.
        List<String> off = lines.stream()
            .filter(l -> !l.courierEnabled()).map(CartLine::name).toList();
        if (!off.isEmpty()) {
            return new Option(DeliveryMethod.COURIER, false, "UNAVAILABLE_ITEMS", 0, 0, false, 0, off);
        }
        // A non-couriable item (e.g. glass board — lorry only) hides courier for the whole cart.
        if (lines.stream().anyMatch(l -> l.boardSizeTier() == null)) {
            return unavailable(DeliveryMethod.COURIER, "MISSING_SIZE_TIER");
        }
        // Large boards (6×3, 6×4, 8×4) can't be couriered to outer Domex areas (owner 2026-07-03) — they
        // courier fine to city-limits/suburbs. Surface the blocking names for the "remove / lorry / contact" prompt.
        if (isOuterCourierArea(courierZone)) {
            List<String> blocked = lines.stream()
                .filter(CartLine::courierOuterBlocked).map(CartLine::name).toList();
            if (!blocked.isEmpty()) {
                return new Option(DeliveryMethod.COURIER, false, "OVERSIZE_OUTER", 0, 0, false, 0, blocked);
            }
        }
        // Domex weight rate (owner 2026-07-16): per board, first kg + additional kgs; packages above
        // 2 ft add the area's per-package handling fee. Computed by the pure engine.
        CourierRateCard rate = courierRates.findById(courierZone)
            .orElseThrow(() -> new IllegalStateException("No courier rate for zone " + courierZone));
        long estimate = 0;
        for (CartLine line : lines) {
            estimate += courierEngine.estimateLine(rate, line.weightKg(), line.boardSizeTier(), line.quantity());
        }
        return new Option(DeliveryMethod.COURIER, true, null, 0, estimate, false, 0, List.of());
    }

    /** Admin per-zone lorry switch (owner 2026-07-05): effective value already resolved per line. */
    private static boolean lorryEnabled(CartLine line, LorryZone zone) {
        return switch (zone) {
            case COLOMBO -> line.lorryColomboEnabled();
            case SUBURB -> line.lorrySuburbEnabled();
            case OUTER -> line.lorryOuterEnabled();
        };
    }

    /**
     * The resolved (variant, zone) lorry cell: priced, gated (with or without its own price — any zone
     * since 2026-07-05), or arranged (blank). The enabled switch is checked before this is called.
     */
    private static LorryCostEngine.Charge lorryCharge(CartLine line, LorryZone zone) {
        Long cell = switch (zone) {
            case COLOMBO -> line.lorryColomboCents();
            case SUBURB -> line.lorrySuburbCents();
            case OUTER -> line.lorryOuterCents();
        };
        Long gate = switch (zone) {
            case COLOMBO -> line.lorryColomboGateCents();
            case SUBURB -> line.lorrySuburbGateCents();
            case OUTER -> line.lorryOuterGateCents();
        };
        if (gate != null) {
            return cell != null ? LorryCostEngine.Charge.gatedPriced(gate, cell)
                                : LorryCostEngine.Charge.gated(gate);
        }
        return cell != null ? LorryCostEngine.Charge.flat(cell) : LorryCostEngine.Charge.ARRANGED;
    }

    /**
     * The flat charge for a gated cell that has individually met its own threshold — same number
     * that {@link #capLorryTotal} reuses as the whole-zone ceiling (owner 2026-07-07, unconditional).
     * Outer keeps its separate, uncapped flat charge.
     */
    private long lorryCapCents(LorryZone zone) {
        DeliverySettings s = settings.findFirstByOrderByIdAsc().orElse(null);
        if (s == null) return 0;
        return switch (zone) {
            case COLOMBO -> s.getLorryCapColomboCents();
            case SUBURB -> s.getLorryCapSuburbCents();
            case OUTER -> s.getGateMetOuterCents();
        };
    }

    /** The "outer areas of Domex" where large boards can't be couriered (owner 2026-07-03). */
    private static boolean isOuterCourierArea(CourierZone zone) {
        return zone == CourierZone.OUTSTATION || zone == CourierZone.FARAWAY;
    }

    private boolean enabled(DeliveryMethod method) {
        return methodConfig.findById(method).map(DeliveryMethodConfig::isEnabled).orElse(true);
    }

    private static Option unavailable(DeliveryMethod method, String reason) {
        return new Option(method, false, reason, 0, 0, false, 0, List.of());
    }

    private static String normalize(String postalCode) {
        return postalCode == null ? null : postalCode.trim();
    }
}
