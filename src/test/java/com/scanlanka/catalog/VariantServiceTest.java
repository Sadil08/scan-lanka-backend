package com.scanlanka.catalog;

import com.scanlanka.catalog.app.VariantService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class VariantServiceTest {

    private final VariantService service = new VariantService();

    @Test
    void noPriceAffectingGroupsYieldsNoVariants() {
        // k=0 → SINGLE-priced product, no variants (FR-CATALOG-7)
        assertThat(service.cartesian(List.of())).isEmpty();
    }

    @Test
    void singleGroupYieldsOnePerOption() {
        // Size {1,2} → 2 variants (FR-CATALOG-6)
        List<List<Long>> variants = service.cartesian(List.of(List.of(1L, 2L)));
        assertThat(variants).hasSize(2);
        assertThat(variants).containsExactlyInAnyOrder(List.of(1L), List.of(2L));
    }

    @Test
    void twoGroupsYieldFullCrossProduct() {
        // Size {1,2} × Thickness {3,4} → 4 variants (Q-2 resolved: full cross-product)
        List<List<Long>> variants = service.cartesian(List.of(List.of(1L, 2L), List.of(3L, 4L)));
        assertThat(variants).hasSize(4);
        assertThat(variants).contains(List.of(1L, 3L), List.of(1L, 4L), List.of(2L, 3L), List.of(2L, 4L));
    }

    @Test
    void threeGroupsMultiply() {
        List<List<Long>> variants =
            service.cartesian(List.of(List.of(1L, 2L), List.of(3L, 4L), List.of(5L, 6L)));
        assertThat(variants).hasSize(8);
    }

    @Test
    void signatureIsStableRegardlessOfInputOrder() {
        assertThat(service.signature(List.of(3L, 1L, 2L)))
            .isEqualTo(service.signature(List.of(1L, 2L, 3L)))
            .isEqualTo("1-2-3");
    }
}
