package com.scanlanka.shared.money;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MoneyTest {

    @Test
    void roundsHalfUp() {
        assertThat(Money.roundHalfUp(100.4)).isEqualTo(100);
        assertThat(Money.roundHalfUp(100.5)).isEqualTo(101);
    }

    @Test
    void rejectsNegative() {
        assertThat(Money.requireNonNegative(0)).isZero();
        assertThat(Money.requireNonNegative(1500)).isEqualTo(1500);
        assertThatThrownBy(() -> Money.requireNonNegative(-1))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void currencyIsLkr() {
        assertThat(Money.CURRENCY).isEqualTo("LKR");
    }
}
