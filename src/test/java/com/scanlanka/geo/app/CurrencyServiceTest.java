package com.scanlanka.geo.app;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class CurrencyServiceTest {

    @Test
    void convertsLkrCentsToUsdIndicative() {
        long usdCents = CurrencyService.convert(300_000, new BigDecimal("300"));
        assertThat(usdCents).isEqualTo(1000);
    }
}
