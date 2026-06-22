package com.scanlanka.catalog.app;

import com.scanlanka.catalog.domain.ProductVariant;

import java.util.Collection;

/** Stock → storefront availability labels (02 FR-8, LOW threshold). */
public final class StockAvailability {

    public static final int LOW_STOCK_THRESHOLD = 5;

    private StockAvailability() {}

    public static String fromQty(Integer stock) {
        if (stock == null) return "IN_STOCK";
        if (stock <= 0) return "OUT_OF_STOCK";
        if (stock <= LOW_STOCK_THRESHOLD) return "LOW_STOCK";
        return "IN_STOCK";
    }

    /** Worst-case chip availability across active variants. */
    public static String fromVariants(Collection<ProductVariant> activeVariants) {
        if (activeVariants.isEmpty()) return "OUT_OF_STOCK";
        boolean anyInStock = false;
        boolean anyLow = false;
        for (ProductVariant v : activeVariants) {
            String a = fromQty(v.getStockQty());
            if ("OUT_OF_STOCK".equals(a)) continue;
            anyInStock = true;
            if ("LOW_STOCK".equals(a)) anyLow = true;
        }
        if (!anyInStock) return "OUT_OF_STOCK";
        return anyLow ? "LOW_STOCK" : "IN_STOCK";
    }
}
