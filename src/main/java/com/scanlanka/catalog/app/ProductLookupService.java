package com.scanlanka.catalog.app;

import com.scanlanka.catalog.domain.Product;
import com.scanlanka.catalog.domain.ProductVariant;
import com.scanlanka.catalog.infra.ProductRepository;
import com.scanlanka.catalog.infra.ProductVariantRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Public catalog port for other features (cart/checkout) to resolve a line's authoritative price +
 * stock without reaching into catalog internals (global/09 — depend on the service, not the repo).
 */
@Service
@Transactional(readOnly = true)
public class ProductLookupService {

    private final ProductRepository products;
    private final ProductVariantRepository variants;

    public ProductLookupService(ProductRepository products, ProductVariantRepository variants) {
        this.products = products;
        this.variants = variants;
    }

    /** Resolved line pricing/stock; stock == null means unlimited. */
    public record LinePricing(String name, long unitPriceCents, Integer stock) {}

    /** Full line info for order snapshots + delivery (sku, handling class). */
    public record OrderLine(Long productId, Long variantId, String sku, String name,
                            String handlingClass, long unitPriceCents, Integer stock) {}

    public java.util.Optional<OrderLine> resolveOrderLine(Long productId, Long variantId) {
        Product p = products.findById(productId)
            .filter(x -> x.isActive() && !x.isArchived())
            .orElse(null);
        if (p == null) return java.util.Optional.empty();
        String handling = p.getHandlingClass().name();
        if (p.isVariantPriced()) {
            if (variantId == null) return java.util.Optional.empty();
            ProductVariant v = variants.findById(variantId)
                .filter(x -> x.getProductId().equals(p.getId()) && x.isActive())
                .orElse(null);
            if (v == null) return java.util.Optional.empty();
            return java.util.Optional.of(new OrderLine(productId, variantId, v.getSku(), p.getName(),
                handling, v.getPriceCents(), v.getStockQty()));
        }
        return java.util.Optional.of(new OrderLine(productId, null, p.getSku(), p.getName(),
            handling, p.getSinglePriceCents(), p.getStockQty()));
    }

    /** Empty when the product/variant is missing, hidden, or invalid for the product. */
    public Optional<LinePricing> resolveLine(Long productId, Long variantId) {
        Product p = products.findById(productId)
            .filter(x -> x.isActive() && !x.isArchived())
            .orElse(null);
        if (p == null) return Optional.empty();

        if (p.isVariantPriced()) {
            if (variantId == null) return Optional.empty();
            ProductVariant v = variants.findById(variantId)
                .filter(x -> x.getProductId().equals(p.getId()) && x.isActive())
                .orElse(null);
            if (v == null) return Optional.empty();
            return Optional.of(new LinePricing(p.getName(), v.getPriceCents(), v.getStockQty()));
        }
        return Optional.of(new LinePricing(p.getName(), p.getSinglePriceCents(), p.getStockQty()));
    }
}
