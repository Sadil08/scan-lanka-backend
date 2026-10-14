package com.scanlanka.catalog.app;

import com.scanlanka.catalog.domain.Product;
import com.scanlanka.catalog.domain.ProductVariant;
import com.scanlanka.checkout.domain.BoardSizeTier;
import com.scanlanka.catalog.domain.SpecGroup;
import com.scanlanka.catalog.domain.SpecOption;
import com.scanlanka.catalog.infra.ProductRepository;
import com.scanlanka.catalog.infra.SpecGroupRepository;
import com.scanlanka.catalog.infra.SpecOptionRepository;
import com.scanlanka.catalog.infra.ProductVariantRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

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
    private final SpecGroupRepository groups;
    private final SpecOptionRepository options;

    public ProductLookupService(ProductRepository products, ProductVariantRepository variants,
                                VariantLabelResolver labels, SpecGroupRepository groups,
                                SpecOptionRepository options) {
        this.products = products;
        this.variants = variants;
        this.labels = labels;
        this.groups = groups;
        this.options = options;
    }

    /** A validated optional (non-price) choice: canonical sorted id list + display label ("Colour: Red"). */
    public record Selection(String optionIds, String label) {}

    /**
     * Validates client-sent option ids against the product's non-price-affecting spec groups (one option
     * per group, unknown/foreign/price-affecting ids dropped) and returns the canonical ids + label.
     * The label is built server-side from catalog data, never taken from the client.
     */
    public Optional<Selection> resolveSelection(Long productId, String optionIds) {
        if (productId == null || optionIds == null || optionIds.isBlank()) return Optional.empty();
        Map<Long, SpecGroup> optional = new HashMap<>();
        for (SpecGroup g : groups.findByProductIdOrderByDisplayOrderAsc(productId)) {
            if (!g.isPriceAffecting()) optional.put(g.getId(), g);
        }
        if (optional.isEmpty()) return Optional.empty();
        Map<Long, SpecOption> chosen = new HashMap<>();   // groupId -> option
        Set<Long> seen = new HashSet<>();
        for (String tok : optionIds.split(",")) {
            long id;
            try { id = Long.parseLong(tok.trim()); } catch (NumberFormatException e) { continue; }
            if (!seen.add(id)) continue;
            SpecOption o = options.findById(id).orElse(null);
            if (o == null || !optional.containsKey(o.getSpecGroupId())) continue;
            chosen.putIfAbsent(o.getSpecGroupId(), o);
        }
        if (chosen.isEmpty()) return Optional.empty();
        List<String> parts = new ArrayList<>();
        for (SpecGroup g : optional.values().stream()
                .sorted(java.util.Comparator.comparingInt(SpecGroup::getDisplayOrder)).toList()) {
            SpecOption o = chosen.get(g.getId());
            if (o != null) parts.add(g.getName() + ": " + o.getValue());
        }
        TreeSet<Long> ids = new TreeSet<>();
        chosen.values().forEach(o -> ids.add(o.getId()));
        return Optional.of(new Selection(
            String.join(",", ids.stream().map(String::valueOf).toList()), String.join(", ", parts)));
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
                            BoardSizeTier boardSizeTier, java.math.BigDecimal weightKg,
                            Long lorryColomboCents, Long lorrySuburbCents, Long lorryOuterCents,
                            Long lorryColomboGateCents, Long lorrySuburbGateCents, Long lorryOuterGateCents,
                            boolean lorryColomboEnabled, boolean lorrySuburbEnabled, boolean lorryOuterEnabled,
                            boolean lorryOuterWhatsapp, boolean courierOuterBlocked,
                            boolean courierEnabled, boolean whatsappOnly) {}

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
                firstNonNull(v.getWeightKg(), p.getWeightKg()),
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
                v.isCourierEnabled() && p.isCourierEnabled(),
                v.isWhatsappOnly() || p.isWhatsappOnly()));
        }
        return java.util.Optional.of(new OrderLine(productId, null, p.getSku(), p.getName(),
            handling, p.getSinglePriceCents(), p.getStockQty(),
            p.getBoardSizeTier(), p.getWeightKg(),
            p.getLorryColomboCents(), p.getLorrySuburbCents(), p.getLorryOuterCents(),
            p.getLorryColomboGateCents(), p.getLorrySuburbGateCents(), p.getLorryOuterGateCents(),
            p.isLorryColomboEnabled(), p.isLorrySuburbEnabled(), p.isLorryOuterEnabled(),
            p.isLorryOuterWhatsapp(), p.isCourierOuterBlocked(), p.isCourierEnabled(), p.isWhatsappOnly()));
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

    /**
     * The display name of any existing product by id, ignoring active/archived state and variant selection.
     * Used for loose references (e.g. quote lines) where a product may be named without a priced order line —
     * a variant-priced product quoted without a specific size still deserves its product name. Empty only when
     * no such product row exists.
     */
    public Optional<String> productName(Long productId) {
        return products.findById(productId).map(Product::getName);
    }
}
