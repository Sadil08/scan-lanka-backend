package com.scanlanka.checkout.app;

import com.scanlanka.checkout.domain.BoardSizeTier;
import com.scanlanka.checkout.domain.CourierZone;
import org.springframework.stereotype.Component;

/**
 * Domex courier estimate (05 delivery-cost-model.md, owner 2026-07-03 — Domex is the only courier).
 * Flat rate per board size tier and courier area. Display-only — courier orders are full COD.
 * Oversize/missing-tier eligibility is decided by {@link DeliveryOptionsService} (policy), not here.
 * Money in integer LKR cents.
 */
@Component
public class CourierEstimateEngine {

    public long estimateLine(long flatCentsPerBoard, int quantity) {
        if (flatCentsPerBoard < 0) throw new IllegalArgumentException("flatCents must be >= 0");
        if (quantity < 0) throw new IllegalArgumentException("quantity must be >= 0");
        return flatCentsPerBoard * quantity;
    }

    public long estimateCart(CourierZone zone, BoardSizeTier tier, int totalBoards, long flatCentsPerBoard) {
        if (zone == null || tier == null) {
            throw new IllegalArgumentException("zone and tier required");
        }
        return estimateLine(flatCentsPerBoard, totalBoards);
    }
}
