package com.scanlanka.checkout;

import com.scanlanka.checkout.app.LorryCostEngine;
import com.scanlanka.checkout.app.LorryCostEngine.Charge;
import com.scanlanka.checkout.app.LorryCostEngine.Line;
import com.scanlanka.checkout.app.LorryCostEngine.Result;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Fixed per-variant per-zone lorry charges (05 delivery-cost-model.md §2.1 / §3). Rs in cents. */
class LorryCostEngineTest {

    private final LorryCostEngine engine = new LorryCostEngine();
    private static final long GATE = 600000; // Rs 6,000

    @Test
    void sumsFixedChargeTimesQuantityWhenOverMinBill() {
        // AC-DELIV-4: 2× a board whose Colombo lorry cell is Rs 800, order over Rs 6,000 → Rs 1,600
        Result r = engine.compute(List.of(new Line(Charge.flat(80000), 2)), 700000, GATE);
        assertThat(r).isEqualTo(Result.available(160000, false));
    }

    @Test
    void sumsAcrossMultipleLines() {
        Result r = engine.compute(
            List.of(new Line(Charge.flat(80000), 1), new Line(Charge.flat(100000), 2)),
            700000, GATE);
        assertThat(r).isEqualTo(Result.available(80000 + 200000, false));
    }

    @Test
    void belowMinBillTheLorryRailIsUnavailable() {
        // global gate: order subtotal Rs 4,900 < Rs 6,000 → no lorry, even though the cell is priced
        Result r = engine.compute(List.of(new Line(Charge.flat(50000), 1)), 490000, GATE);
        assertThat(r).isEqualTo(Result.unavailable("MIN_BILL_NOT_MET"));
    }

    @Test
    void exactlyAtMinBillDoesNotQualify() {
        // owner: "greater than 6,000" — strictly more, so Rs 6,000 exactly is not enough
        Result r = engine.compute(List.of(new Line(Charge.flat(50000), 1)), 600000, GATE);
        assertThat(r).isEqualTo(Result.unavailable("MIN_BILL_NOT_MET"));
    }

    @Test
    void justOverMinBillQualifies() {
        Result r = engine.compute(List.of(new Line(Charge.flat(50000), 1)), 600001, GATE);
        assertThat(r).isEqualTo(Result.available(50000, false));
    }

    @Test
    void unpricedCellStillOffersLorryAndFlagsArranged() {
        // far/unpriced cell: lorry still offered, admin arranges that line's cost; it adds 0 to prepaid
        Result r = engine.compute(
            List.of(new Line(Charge.flat(80000), 1), new Line(Charge.ARRANGED, 1)),
            700000, GATE);
        assertThat(r).isEqualTo(Result.available(80000, true));
    }
}
