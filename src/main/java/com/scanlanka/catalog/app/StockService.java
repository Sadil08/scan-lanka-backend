package com.scanlanka.catalog.app;

import com.scanlanka.catalog.domain.Product;
import com.scanlanka.catalog.domain.ProductVariant;
import com.scanlanka.catalog.infra.ProductRepository;
import com.scanlanka.catalog.infra.ProductVariantRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Catalog stock port (global/09). Atomic decrement on order confirmation (T-10): unlimited stock
 * (null) is a no-op; limited stock decrements conditionally and throws if insufficient (never oversells).
 */
@Service
public class StockService {

    private final ProductRepository products;
    private final ProductVariantRepository variants;

    public StockService(ProductRepository products, ProductVariantRepository variants) {
        this.products = products;
        this.variants = variants;
    }

    @Transactional
    public void decrement(Long productId, Long variantId, int qty) {
        if (variantId != null) {
            ProductVariant v = variants.findById(variantId).orElse(null);
            if (v == null || v.getStockQty() == null) return;     // unlimited / gone → no-op
            if (variants.decrementIfAvailable(variantId, qty) == 0) throw oversold();
        } else {
            Product p = products.findById(productId).orElse(null);
            if (p == null || p.getStockQty() == null) return;      // unlimited → no-op
            if (products.decrementIfAvailable(productId, qty) == 0) throw oversold();
        }
    }

    /** Restock on cancel/refund when disposition is RESTOCK (16 FR-RETURN-4). */
    @Transactional
    public void increment(Long productId, Long variantId, int qty) {
        if (variantId != null) {
            ProductVariant v = variants.findById(variantId).orElse(null);
            if (v == null || v.getStockQty() == null) return;
            variants.incrementStock(variantId, qty);
        } else if (productId != null) {
            Product p = products.findById(productId).orElse(null);
            if (p == null || p.getStockQty() == null) return;
            products.incrementStock(productId, qty);
        }
    }

    private static ResponseStatusException oversold() {
        return new ResponseStatusException(HttpStatus.CONFLICT, "OVERSOLD");
    }
}
