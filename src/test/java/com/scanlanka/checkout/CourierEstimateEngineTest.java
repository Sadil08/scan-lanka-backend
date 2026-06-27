package com.scanlanka.checkout;

import com.scanlanka.checkout.app.CourierEstimateEngine;
import com.scanlanka.checkout.app.CourierEstimateEngine.Rate;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Worked examples from 05 delivery-cost-model.md §2.2 (Rs in cents). */
class CourierEstimateEngineTest {

    private final CourierEstimateEngine engine = new CourierEstimateEngine();

    // Citrek rate card (08): base / per-kg per zone, in cents.
    private final Rate colombo1to15 = new Rate(47000, 18500);   // Rs 470 + Rs 185/kg
    private final Rate other        = new Rate(61000, 21000);   // Rs 610 + Rs 210/kg
    private final Rate jaffnaNorth  = new Rate(75000, 23000);   // Rs 750 + Rs 230/kg

    @Test
    void fiveKgToColombo1To15() {
        // 5×185 + 470 = Rs 1,395
        assertThat(engine.estimate(colombo1to15, 5)).isEqualTo(139500);
    }

    @Test
    void fiveKgToOther() {
        // 5×210 + 610 = Rs 1,660
        assertThat(engine.estimate(other, 5)).isEqualTo(166000);
    }

    @Test
    void fiveKgToJaffnaNorth() {
        // 5×230 + 750 = Rs 1,900
        assertThat(engine.estimate(jaffnaNorth, 5)).isEqualTo(190000);
    }

    @Test
    void oneKgIsPerKgPlusBase() {
        // 1×185 + 470 = Rs 655 (per-kg applies to the whole weight + a flat base)
        assertThat(engine.estimate(colombo1to15, 1)).isEqualTo(65500);
    }

    @Test
    void fractionalWeightIsRounded() {
        // 2.5 × 185 = 462.5 → rounds to Rs 462.50 (46250c) + 470 base
        assertThat(engine.estimate(colombo1to15, 2.5)).isEqualTo(46250 + 47000);
    }

    @Test
    void zeroWeightIsJustTheBase() {
        assertThat(engine.estimate(colombo1to15, 0)).isEqualTo(47000);
    }

    @Test
    void negativeWeightRejected() {
        assertThatThrownBy(() -> engine.estimate(colombo1to15, -1))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
