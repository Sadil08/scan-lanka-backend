package com.scanlanka.checkout;

import com.scanlanka.checkout.app.CourierEstimateEngine;
import com.scanlanka.checkout.domain.BoardSizeTier;
import com.scanlanka.checkout.domain.CourierRateCard;
import com.scanlanka.checkout.domain.CourierZone;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Domex weight rate per board + above-2ft handling fee (owner 2026-07-16 rate card, V48). */
class CourierEstimateEngineTest {

    private final CourierEstimateEngine engine = new CourierEstimateEngine();

    private static final CourierRateCard CITY = new CourierRateCard(CourierZone.CITY_LIMITS, 38000, 18000, 75000);
    private static final CourierRateCard FARAWAY = new CourierRateCard(CourierZone.FARAWAY, 65000, 25000, 200000);

    @Test
    void oneKgUnder2FtIsFirstKgOnly() {
        assertThat(engine.estimateBoard(CITY, BigDecimal.ONE, BoardSizeTier.UNDER_2FT)).isEqualTo(38000);
    }

    @Test
    void additionalKgsAddPerKgRate() {
        // 3 kg under 2 ft: Rs 380 + 2 × Rs 180
        assertThat(engine.estimateBoard(CITY, new BigDecimal("3"), BoardSizeTier.UNDER_2FT)).isEqualTo(74000);
    }

    @Test
    void above2FtAddsHandlingFee() {
        // 1 kg above 2 ft, city limits: Rs 380 + Rs 750
        assertThat(engine.estimateBoard(CITY, BigDecimal.ONE, BoardSizeTier.BETWEEN_2FT_6FT)).isEqualTo(113000);
    }

    @Test
    void ownersWorkedExampleTwentyKgFaraway() {
        // 5 x 4 magnetic board, 20 kg, faraway: 1st kg Rs 650 + 19 × Rs 250 + Rs 2,000 = Rs 7,400
        assertThat(engine.estimateBoard(FARAWAY, new BigDecimal("20"), BoardSizeTier.BETWEEN_2FT_6FT))
            .isEqualTo(740000);
    }

    @Test
    void fractionalWeightRoundsUpToNextKg() {
        // 2.3 kg bills as 3 kg
        assertThat(engine.estimateBoard(CITY, new BigDecimal("2.3"), BoardSizeTier.UNDER_2FT)).isEqualTo(74000);
    }

    @Test
    void missingWeightBillsFirstKg() {
        assertThat(engine.estimateBoard(CITY, null, BoardSizeTier.UNDER_2FT)).isEqualTo(38000);
    }

    @Test
    void quantityMultipliesPerBoardCharge() {
        assertThat(engine.estimateLine(CITY, BigDecimal.ONE, BoardSizeTier.BETWEEN_2FT_6FT, 3)).isEqualTo(339000);
    }

    @Test
    void negativeQuantityRejected() {
        assertThatThrownBy(() -> engine.estimateLine(CITY, BigDecimal.ONE, BoardSizeTier.UNDER_2FT, -1))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
