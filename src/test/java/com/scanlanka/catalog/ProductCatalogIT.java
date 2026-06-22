package com.scanlanka.catalog;

import com.scanlanka.AbstractIntegrationTest;
import com.scanlanka.catalog.app.ProductService;
import com.scanlanka.catalog.domain.PriceMode;
import com.scanlanka.catalog.domain.Product;
import com.scanlanka.catalog.infra.ProductRepository;
import com.scanlanka.catalog.infra.ProductVariantRepository;
import com.scanlanka.catalog.web.dto.ProductRequests.CreateProductRequest;
import com.scanlanka.catalog.web.dto.ProductRequests.GroupInput;
import com.scanlanka.catalog.web.dto.ProductRequests.VariantInput;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Real-DB catalog create (01 ProductCatalogIT). Proves Flyway V2 + JPA validate, the variant
 * cross-product (price-affecting only), the price range, and coverage enforcement.
 */
class ProductCatalogIT extends AbstractIntegrationTest {

    @Autowired ProductService productService;
    @Autowired ProductRepository products;
    @Autowired ProductVariantRepository variants;

    @Test
    void createsVariantsFromPriceAffectingGroupsOnly() {
        // Size (price-affecting, 2 options) × Frame (informational, 2 options) ⇒ 2 variants, not 4 (AC-CATALOG-2)
        Long id = productService.create(new CreateProductRequest(
            null, "Carrom Board", null, "A board", "details", "Boards", "FRAGILE_GLASS", null, null,
            List.of(new GroupInput("Size", true, List.of("Small", "Large")),
                    new GroupInput("Frame", false, List.of("Wood", "Metal"))),
            List.of(new VariantInput(List.of("Small"), 8000, null, 10),
                    new VariantInput(List.of("Large"), 12000, null, 5))));

        Product p = products.findById(id).orElseThrow();
        assertThat(p.getPriceMode()).isEqualTo(PriceMode.VARIANT);
        assertThat(variants.findByProductId(id)).hasSize(2);
        // price range over variants (AC-CATALOG-3)
        assertThat(p.getPriceRangeMinCents()).isEqualTo(8000);
        assertThat(p.getPriceRangeMaxCents()).isEqualTo(12000);
    }

    @Test
    void singleProductHasNoVariants() {
        Long id = productService.create(new CreateProductRequest(
            null, "Whiteboard Marker", null, "Marker", null, "Accessories", null, 100, 250L,
            List.of(), List.of()));

        Product p = products.findById(id).orElseThrow();
        assertThat(p.getPriceMode()).isEqualTo(PriceMode.SINGLE);
        assertThat(p.getSinglePriceCents()).isEqualTo(250L);
        assertThat(p.getStockQty()).isEqualTo(100);
        assertThat(variants.findByProductId(id)).isEmpty();
    }

    @Test
    void rejectsIncompleteVariantCoverage() {
        // Size has 2 options but only one variant price supplied → coverage mismatch (AC-CATALOG: 400)
        assertThatThrownBy(() -> productService.create(new CreateProductRequest(
            null, "Notice Board", null, null, null, null, null, null, null,
            List.of(new GroupInput("Size", true, List.of("Small", "Large"))),
            List.of(new VariantInput(List.of("Small"), 5000, null, null)))))
            .hasMessageContaining("VARIANT_COVERAGE_MISMATCH");
    }
}
