package com.scanlanka.checkout;

import com.scanlanka.checkout.app.CourierEstimateEngine;
import com.scanlanka.checkout.domain.BoardSizeTier;
import com.scanlanka.checkout.domain.CourierZone;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Flat rate per board size tier (client rate card, 2026). */
class CourierEstimateEngineTest {

    private final CourierEstimateEngine engine = new CourierEstimateEngine();

    @Test
    void oneBoardCityLimitsBetween2And6Ft() {
        assertThat(engine.estimateLine(100000, 1)).isEqualTo(100000); // Rs 1,000
    }

    @Test
    void twoBoardsUnder2FtSuburbs() {
        assertThat(engine.estimateLine(75000, 2)).isEqualTo(150000); // 2 × Rs 750
    }

    @Test
    void quantityMultipliesFlatRate() {
        assertThat(engine.estimateCart(CourierZone.FARAWAY, BoardSizeTier.BETWEEN_2FT_6FT, 3, 200000))
            .isEqualTo(600000);
    }

    @Test
    void negativeQuantityRejected() {
        assertThatThrownBy(() -> engine.estimateLine(100000, -1))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
