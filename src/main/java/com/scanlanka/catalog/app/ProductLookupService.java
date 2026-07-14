package com.scanlanka.catalog.app;

import com.scanlanka.catalog.domain.Product;
import com.scanlanka.catalog.domain.ProductVariant;
import com.scanlanka.checkout.domain.BoardSizeTier;
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
    private final VariantLabelResolver labels;

    public ProductLookupService(ProductRepository products, ProductVariantRepository variants,
                                VariantLabelResolver labels) {
        this.products = products;
        this.variants = variants;
        this.labels = labels;
    }

    /** Resolved line pricing/stock; stock == null means unlimited. */
    public record LinePricing(String name, long unitPriceCents, Integer stock) {}

    /**
     * Full line info for order snapshots + the two-rail delivery engine (05/17). Delivery attributes
     * (board size tier, per-zone lorry charges, whatsapp-only) are resolved <b>per variant, falling back to the
     * product</b> when variant-priced — mirroring price/stock resolution (FR-CATALOG-14b).
     */
    public record OrderLine(Long productId, Long variantId, String sku, String name,
                            String handlingClass, long unitPriceCents, Integer stock,
                            BoardSizeTier boardSizeTier,
                            Long lorryColomboCents, Long lorrySuburbCents, Long lorryOuterCents,
                            Long lorryColomboGateCents, Long lorrySuburbGateCents, Long lorryOuterGateCents,
                            boolean lorryColomboEnabled, boolean lorrySuburbEnabled, boolean lorryOuterEnabled,
                            boolean lorryOuterWhatsapp, boolean courierOuterBlocked, boolean whatsappOnly) {}

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
            String name = labels.nameWithSize(p.getName(), v.getOptionsSignature());
            return java.util.Optional.of(new OrderLine(productId, variantId, v.getSku(), name,
                handling, v.getPriceCents(), v.getStockQty(),
                firstNonNull(v.getBoardSizeTier(), p.getBoardSizeTier()),
                firstNonNull(v.getLorryColomboCents(), p.getLorryColomboCents()),
                firstNonNull(v.getLorrySuburbCents(), p.getLorrySuburbCents()),
                firstNonNull(v.getLorryOuterCents(), p.getLorryOuterCents()),
                firstNonNull(v.getLorryColomboGateCents(), p.getLorryColomboGateCents()),
                firstNonNull(v.getLorrySuburbGateCents(), p.getLorrySuburbGateCents()),
                firstNonNull(v.getLorryOuterGateCents(), p.getLorryOuterGateCents()),
                // ON only when neither level switched the zone off (default true on both)
                v.isLorryColomboEnabled() && p.isLorryColomboEnabled(),
                v.isLorrySuburbEnabled() && p.isLorrySuburbEnabled(),
                v.isLorryOuterEnabled() && p.isLorryOuterEnabled(),
                v.isLorryOuterWhatsapp() || p.isLorryOuterWhatsapp(),
                v.isCourierOuterBlocked() || p.isCourierOuterBlocked(),
                v.isWhatsappOnly() || p.isWhatsappOnly()));
        }
        return java.util.Optional.of(new OrderLine(productId, null, p.getSku(), p.getName(),
            handling, p.getSinglePriceCents(), p.getStockQty(),
            p.getBoardSizeTier(), p.getLorryColomboCents(), p.getLorrySuburbCents(), p.getLorryOuterCents(),
            p.getLorryColomboGateCents(), p.getLorrySuburbGateCents(), p.getLorryOuterGateCents(),
            p.isLorryColomboEnabled(), p.isLorrySuburbEnabled(), p.isLorryOuterEnabled(),
            p.isLorryOuterWhatsapp(), p.isCourierOuterBlocked(), p.isWhatsappOnly()));
    }

    private static <T> T firstNonNull(T a, T b) {
        return a != null ? a : b;
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
            String name = labels.nameWithSize(p.getName(), v.getOptionsSignature());
            return Optional.of(new LinePricing(name, v.getPriceCents(), v.getStockQty()));
        }
        return Optional.of(new LinePricing(p.getName(), p.getSinglePriceCents(), p.getStockQty()));
    }
}
