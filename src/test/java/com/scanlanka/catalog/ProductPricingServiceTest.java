package com.scanlanka.catalog;

import com.scanlanka.catalog.app.ProductPricingService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;

class ProductPricingServiceTest {

    private final ProductPricingService service = new ProductPricingService();

    @Test
    void coverageMatchesExactly() {
        assertThatCode(() -> service.assertCoverage(Set.of("1-3", "1-4"), Set.of("1-4", "1-3")))
            .doesNotThrowAnyException();
    }

    @Test
    void missingVariantPriceRejected() {
        assertThatThrownBy(() -> service.assertCoverage(Set.of("1-3", "1-4"), Set.of("1-3")))
            .hasMessageContaining("VARIANT_COVERAGE_MISMATCH");
    }

    @Test
    void extraVariantPriceRejected() {
        assertThatThrownBy(() -> service.assertCoverage(Set.of("1-3"), Set.of("1-3", "9-9")))
            .hasMessageContaining("VARIANT_COVERAGE_MISMATCH");
    }

    @Test
    void priceRangeIsMinToMax() {
        assertThat(service.priceRange(List.of(12000L, 8000L, 10000L))).containsExactly(8000L, 12000L);
    }

    @Test
    void priceRangeRequiresAtLeastOnePrice() {
        assertThatThrownBy(() -> service.priceRange(List.of()))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
