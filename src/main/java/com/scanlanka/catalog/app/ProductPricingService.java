package com.scanlanka.catalog.app;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Collection;
import java.util.Collections;
import java.util.Set;

/**
 * Product pricing rules (01-product-catalog FR-CATALOG-6/7/8). Validates that admin-provided variant
 * prices cover EXACTLY the cartesian product (no missing/extra), and derives the chip price range.
 */
@Service
public class ProductPricingService {

    /** Provided variant signatures must equal the expected cartesian set exactly. */
    public void assertCoverage(Set<String> expected, Set<String> provided) {
        if (!expected.equals(provided)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "VARIANT_COVERAGE_MISMATCH");
        }
    }

    /** Min/max over variant prices (cents) for the storefront chip range. Requires ≥1 price. */
    public long[] priceRange(Collection<Long> variantPricesCents) {
        if (variantPricesCents == null || variantPricesCents.isEmpty()) {
            throw new IllegalArgumentException("price range requires at least one variant price");
        }
        return new long[] { Collections.min(variantPricesCents), Collections.max(variantPricesCents) };
    }
}
