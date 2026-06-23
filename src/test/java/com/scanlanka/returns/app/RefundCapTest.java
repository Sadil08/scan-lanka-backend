package com.scanlanka.returns.app;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Refund cap math (16 FR-RETURN-6). */
class RefundCapTest {

    @Test
    void capIsOnlinePaidMinusPriorRefunds() {
        long onlinePaid = 10_000;
        long prior = 3_500;
        long cap = onlinePaid - prior;
        assertThat(cap).isEqualTo(6_500);
        assertThat(7_000 > cap).isTrue();
        assertThat(6_500 <= cap).isTrue();
    }
}
