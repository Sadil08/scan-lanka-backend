package com.scanlanka.checkout.app;

import org.springframework.stereotype.Component;

/**
 * Citrek courier estimate (05 delivery-cost-model §2.2, 17 FR-DELIV-5). Pure, deterministic, NO AI.
 *
 * <p>estimate = round(weightKg × perKgCents) + baseCents, per resolved courier zone. This figure is
 * <b>display-only</b> — courier orders are full COD, so it is never charged. The caller resolves the
 * rate from the courier rate card (admin config, 08) and must ensure every cart line has a weight
 * (a missing weight hides the courier rail entirely — FR-DELIV-6). Money in integer LKR cents.
 */
@Component
public class CourierEstimateEngine {

    /** Resolved rate-card row for the order's courier zone (COLOMBO_1_15 / OTHER / JAFFNA_NORTH). */
    public record Rate(long baseCents, long perKgCents) {}

    /** @param totalWeightKg Σ(variant weight_kg × qty) across the cart; must be ≥ 0. */
    public long estimate(Rate rate, double totalWeightKg) {
        if (totalWeightKg < 0) {
            throw new IllegalArgumentException("totalWeightKg must be >= 0");
        }
        return Math.round(totalWeightKg * rate.perKgCents()) + rate.baseCents();
    }
}
