package com.scanlanka.checkout.app;

import org.springframework.stereotype.Component;

import java.util.List;

/**
 * In-house lorry cost (05 delivery-cost-model.md §2.1, 17 FR-DELIV-3/3b). Pure, deterministic, NO AI.
 * Money in integer LKR cents.
 *
 * <p>Per-cell model (owner 2026-07-03, replacing the old global min-bill gate). Each resolved
 * (variant, zone) cell is one of:
 * <ul>
 *   <li><b>FLAT</b> — a fixed price; contributes {@code amount × qty}.</li>
 *   <li><b>GATED</b> — a minimum-bill threshold (the sheet's "bill value must be more than 3,000 / 6,000"
 *       cells; any zone since 2026-07-05). The lorry is offered only when the order subtotal is strictly
 *       greater than <b>every</b> gated threshold in the cart. When met, a gated cell that also carries a
 *       <b>price</b> contributes {@code price × qty}; a gated cell with no price contributes 0 and a single
 *       flat per-order "gate-met" charge for the zone (Rs 500 Colombo / Rs 800 Suburb, admin-set) is added
 *       <b>once</b>. If any threshold is unmet, the whole rail is unavailable with the largest unmet
 *       threshold so the UI can say "add Rs N more".</li>
 *   <li><b>ARRANGED</b> — a blank cell; the lorry is still offered, the line contributes 0, and the order
 *       is flagged {@code someArranged} for the admin to arrange that item's far-delivery cost (owner
 *       2026-06-27).</li>
 * </ul>
 * Outer-zone WhatsApp items ({@code lorry_outer_whatsapp}) are filtered out by the caller before this
 * engine runs (they hide the lorry rail entirely for outer addresses).
 */
@Component
public class LorryCostEngine {

    public enum Kind { FLAT, GATED, ARRANGED }

    /**
     * The resolved (variant, zone) lorry cell.
     * {@code amountCents}: the per-unit price — for {@code FLAT} always charged; for {@code GATED} charged
     * when the gate is met (0 ⇒ no cell price, fall back to the zone's flat gate-met charge).
     * {@code gateThresholdCents} is meaningful only for {@code GATED}.
     */
    public record Charge(Kind kind, long amountCents, long gateThresholdCents) {
        public static Charge flat(long amountCents) { return new Charge(Kind.FLAT, amountCents, 0); }
        /** A minimum-bill cell with no own price: gate-met ⇒ the zone's flat charge (once per order). */
        public static Charge gated(long thresholdCents) { return new Charge(Kind.GATED, 0, thresholdCents); }
        /** A minimum-bill cell with its own per-unit price: gate-met ⇒ {@code amountCents × qty}. */
        public static Charge gatedPriced(long thresholdCents, long amountCents) {
            return new Charge(Kind.GATED, amountCents, thresholdCents);
        }
        /** No listed price for this (variant, zone): lorry still offered, admin arranges the cost manually. */
        public static final Charge ARRANGED = new Charge(Kind.ARRANGED, 0, 0);
    }

    public record Line(Charge charge, int quantity) {}

    /** Either the lorry delivery quote, or a reason the lorry rail isn't offered for this order. */
    public sealed interface Result {
        /**
         * @param prepaidCents  Σ priced lines + (one flat gate-met charge, if any gated line qualified)
         * @param someArranged  true if ≥1 line had a blank cell — admin will confirm the rest manually
         */
        record Available(long prepaidCents, boolean someArranged) implements Result {}
        /**
         * @param reason      machine code (e.g. {@code MIN_BILL_NOT_MET})
         * @param detailCents for {@code MIN_BILL_NOT_MET}, the largest unmet threshold; else 0
         */
        record Unavailable(String reason, long detailCents) implements Result {}

        static Result available(long prepaidCents, boolean someArranged) {
            return new Available(prepaidCents, someArranged);
        }
        static Result unavailable(String reason, long detailCents) {
            return new Unavailable(reason, detailCents);
        }
    }

    /**
     * @param subtotalCents      the order's product subtotal (drives every gated cell's threshold check)
     * @param gateMetFlatCents   the resolved zone's flat per-order gate-met charge (Rs 500/800), added once
     *                           when ≥1 gated line qualifies; pass 0 for zones with no gate-met charge
     */
    public Result compute(List<Line> lines, long subtotalCents, long gateMetFlatCents) {
        long largestUnmetThreshold = 0;
        for (Line line : lines) {
            Charge c = line.charge();
            if (c.kind() == Kind.GATED && subtotalCents <= c.gateThresholdCents()) {
                largestUnmetThreshold = Math.max(largestUnmetThreshold, c.gateThresholdCents());
            }
        }
        if (largestUnmetThreshold > 0) {
            return Result.unavailable("MIN_BILL_NOT_MET", largestUnmetThreshold);
        }

        long prepaid = 0;
        boolean someArranged = false;
        boolean anyUnpricedGateMet = false;
        for (Line line : lines) {
            Charge charge = line.charge();
            switch (charge.kind()) {
                case FLAT -> prepaid += charge.amountCents() * (long) line.quantity();
                case GATED -> {                       // threshold already met (checked above)
                    if (charge.amountCents() > 0) {
                        prepaid += charge.amountCents() * (long) line.quantity();  // cell's own price
                    } else {
                        anyUnpricedGateMet = true;    // no cell price → zone flat fee
                    }
                }
                case ARRANGED -> someArranged = true; // offered anyway; admin arranges the far-delivery cost
            }
        }
        if (anyUnpricedGateMet) {
            prepaid += gateMetFlatCents;              // one flat charge per order, not per line/unit
        }
        return Result.available(prepaid, someArranged);
    }
}
