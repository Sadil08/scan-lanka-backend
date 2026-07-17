package com.scanlanka.checkout.app;

import com.scanlanka.checkout.domain.BoardSizeTier;
import com.scanlanka.checkout.domain.CourierRateCard;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Domex courier estimate (05 delivery-cost-model.md, owner 2026-07-16 rate card — Domex is the only
 * courier). Weight-based per board: first kg + (ceil(weight) - 1) × additional kg for the area; a
 * package above 2 ft ({@link BoardSizeTier} other than {@code UNDER_2FT}) adds the area's
 * per-package handling fee (e.g. 5×4 magnetic board, 20 kg, faraway: 1st kg + 19 × add-kg + Rs 2,000).
 * Display-only — courier orders are full COD. Eligibility (courier switch, missing tier, oversize
 * outer) is decided by {@link DeliveryOptionsService} (policy), not here. Money in integer LKR cents.
 */
@Component
public class CourierEstimateEngine {

    /** Charge for ONE board; a missing/zero weight bills as the first kg only. */
    public long estimateBoard(CourierRateCard rate, BigDecimal weightKg, BoardSizeTier tier) {
        if (rate == null || tier == null) throw new IllegalArgumentException("rate and tier required");
        int kg = weightKg == null ? 1 : Math.max(1, weightKg.setScale(0, RoundingMode.CEILING).intValue());
        long cents = rate.getFirstKgCents() + (kg - 1L) * rate.getAddlKgCents();
        if (tier != BoardSizeTier.UNDER_2FT) {
            cents += rate.getHandlingOver2ftCents();
        }
        return cents;
    }

    public long estimateLine(CourierRateCard rate, BigDecimal weightKg, BoardSizeTier tier, int quantity) {
        if (quantity < 0) throw new IllegalArgumentException("quantity must be >= 0");
        return estimateBoard(rate, weightKg, tier) * quantity;
    }
}
