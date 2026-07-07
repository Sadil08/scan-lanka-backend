package com.scanlanka.checkout;

import com.scanlanka.checkout.app.LorryCostEngine;
import com.scanlanka.checkout.app.LorryCostEngine.Charge;
import com.scanlanka.checkout.app.LorryCostEngine.Line;
import com.scanlanka.checkout.app.LorryCostEngine.Result;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Per-cell lorry model (05 delivery-cost-model.md §2.1 / §3, owner 2026-07-03). Rs in cents.
 * Priced cells sum × qty; gated cells gate on a per-cell threshold and, when met, add ONE flat
 * per-order charge for the zone; blank cells keep the lorry offered and flag {@code someArranged}.
 */
class LorryCostEngineTest {

    private final LorryCostEngine engine = new LorryCostEngine();
    private static final long FLAT_COLOMBO = 50000;  // Rs 500 gate-met charge for Colombo
    private static final long GATE_3000 = 300000;
    private static final long GATE_6000 = 600000;

    @Test
    void sumsFixedChargeTimesQuantity() {
        // AC-DELIV: 2× a board whose Colombo lorry cell is Rs 800 → Rs 1,600 (no gate involved)
        Result r = engine.compute(List.of(new Line(Charge.flat(80000), 2)), 700000, FLAT_COLOMBO);
        assertThat(r).isEqualTo(Result.available(160000, false));
    }

    @Test
    void sumsAcrossMultipleFlatLines() {
        Result r = engine.compute(
            List.of(new Line(Charge.flat(80000), 1), new Line(Charge.flat(100000), 2)),
            700000, FLAT_COLOMBO);
        assertThat(r).isEqualTo(Result.available(80000 + 200000, false));
    }

    @Test
    void gatedBelowThresholdMakesLorryUnavailable() {
        // subtotal Rs 1,100 <= Rs 3,000 gate → unavailable, threshold reported for "add Rs N more"
        Result r = engine.compute(List.of(new Line(Charge.gated(GATE_3000), 1)), 110000, FLAT_COLOMBO);
        assertThat(r).isEqualTo(Result.unavailable("MIN_BILL_NOT_MET", GATE_3000));
    }

    @Test
    void gatedExactlyAtThresholdDoesNotQualify() {
        // owner: strictly greater — exactly Rs 3,000 is not enough
        Result r = engine.compute(List.of(new Line(Charge.gated(GATE_3000), 1)), GATE_3000, FLAT_COLOMBO);
        assertThat(r).isEqualTo(Result.unavailable("MIN_BILL_NOT_MET", GATE_3000));
    }

    @Test
    void gatedJustOverThresholdAddsOneFlatCharge() {
        // subtotal just over Rs 3,000 → lorry offered at the flat Rs 500 gate-met charge
        Result r = engine.compute(List.of(new Line(Charge.gated(GATE_3000), 1)), GATE_3000 + 1, FLAT_COLOMBO);
        assertThat(r).isEqualTo(Result.available(FLAT_COLOMBO, false));
    }

    @Test
    void largestUnmetThresholdIsReported() {
        // one gate met (3,000), one unmet (6,000) at subtotal Rs 4,000 → unavailable with 6,000
        Result r = engine.compute(
            List.of(new Line(Charge.gated(GATE_3000), 1), new Line(Charge.gated(GATE_6000), 1)),
            400000, FLAT_COLOMBO);
        assertThat(r).isEqualTo(Result.unavailable("MIN_BILL_NOT_MET", GATE_6000));
    }

    @Test
    void flatGateMetChargeAddedOncePerOrderNotPerLine() {
        // two gated lines both met → still exactly ONE flat charge, not two
        Result r = engine.compute(
            List.of(new Line(Charge.gated(GATE_3000), 3), new Line(Charge.gated(GATE_3000), 2)),
            700000, FLAT_COLOMBO);
        assertThat(r).isEqualTo(Result.available(FLAT_COLOMBO, false));
    }

    @Test
    void pricedPlusGatedMetSumsPricedThenAddsFlatOnce() {
        // Rs 300 priced cell + a gated-met cell → 30000 + 50000 flat
        Result r = engine.compute(
            List.of(new Line(Charge.flat(30000), 1), new Line(Charge.gated(GATE_3000), 1)),
            400000, FLAT_COLOMBO);
        assertThat(r).isEqualTo(Result.available(30000 + FLAT_COLOMBO, false));
    }

    @Test
    void blankCellStillOffersLorryAndFlagsArranged() {
        Result r = engine.compute(
            List.of(new Line(Charge.flat(80000), 1), new Line(Charge.ARRANGED, 1)),
            700000, FLAT_COLOMBO);
        assertThat(r).isEqualTo(Result.available(80000, true));
    }

    @Test
    void gatedMetDoesNotSetArranged() {
        Result r = engine.compute(List.of(new Line(Charge.gated(GATE_3000), 1)), 700000, FLAT_COLOMBO);
        assertThat(r).isEqualTo(Result.available(FLAT_COLOMBO, false));
    }

    @Test
    void gatedCellWithOwnPriceChargesPerUnitInsteadOfFlat() {
        // owner 2026-07-05: a gated cell may carry its own price — price × qty, no flat charge
        Result r = engine.compute(List.of(new Line(Charge.gatedPriced(GATE_3000, 30000), 3)),
            400000, FLAT_COLOMBO);
        assertThat(r).isEqualTo(Result.available(90000, false));
    }

    @Test
    void gatedPricedBelowThresholdStillUnavailable() {
        Result r = engine.compute(List.of(new Line(Charge.gatedPriced(GATE_3000, 30000), 1)),
            110000, FLAT_COLOMBO);
        assertThat(r).isEqualTo(Result.unavailable("MIN_BILL_NOT_MET", GATE_3000));
    }

    @Test
    void mixedPricedAndUnpricedGatesChargeBothWaysOnce() {
        // one gated cell with price + one without → price×qty + ONE flat charge
        Result r = engine.compute(
            List.of(new Line(Charge.gatedPriced(GATE_3000, 30000), 2), new Line(Charge.gated(GATE_3000), 5)),
            700000, FLAT_COLOMBO);
        assertThat(r).isEqualTo(Result.available(60000 + FLAT_COLOMBO, false));
    }
}
