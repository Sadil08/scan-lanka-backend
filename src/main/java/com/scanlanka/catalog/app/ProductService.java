package com.scanlanka.catalog.app;

import com.scanlanka.catalog.domain.HandlingClass;
import com.scanlanka.catalog.domain.PriceMode;
import com.scanlanka.catalog.domain.Product;
import com.scanlanka.catalog.domain.ProductVariant;
import com.scanlanka.catalog.domain.SpecGroup;
import com.scanlanka.catalog.domain.SpecOption;
import com.scanlanka.catalog.infra.ProductRepository;
import com.scanlanka.catalog.infra.ProductVariantRepository;
import com.scanlanka.catalog.infra.SpecGroupRepository;
import com.scanlanka.catalog.infra.SpecOptionRepository;
import com.scanlanka.catalog.web.dto.ProductRequests.CreateProductRequest;
import com.scanlanka.catalog.web.dto.ProductRequests.DeliveryAttrs;
import com.scanlanka.catalog.web.dto.ProductRequests.GroupInput;
import com.scanlanka.catalog.web.dto.ProductRequests.UpdateProductRequest;
import com.scanlanka.catalog.web.dto.ProductRequests.VariantInput;
import com.scanlanka.catalog.web.dto.ProductResponses.VariantPreviewResponse;
import com.scanlanka.catalog.web.dto.ProductResponses.VariantPreviewRowDTO;
import com.scanlanka.catalog.app.StockAvailability;
import com.scanlanka.inbox.app.CustomerInboxService;
import com.scanlanka.order.infra.OrderItemRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Product create (01-product-catalog). Wires the variant engine + pricing rules to persistence:
 * groups/options → cartesian variants → coverage check → persist + denormalized price range.
 */
@Service
public class ProductService {

    private static final SecureRandom RNG = new SecureRandom();

    private final ProductRepository products;
    private final SpecGroupRepository groups;
    private final SpecOptionRepository options;
    private final ProductVariantRepository variants;
    private final VariantService variantService;
    private final ProductPricingService pricing;
    private final OrderItemRepository orderItems;
    private final CatalogCacheEvictor cacheEvictor;
    private final CustomerInboxService inbox;

    public ProductService(ProductRepository products, SpecGroupRepository groups, SpecOptionRepository options,
                          ProductVariantRepository variants, VariantService variantService,
                          ProductPricingService pricing, OrderItemRepository orderItems,
                          CatalogCacheEvictor cacheEvictor, CustomerInboxService inbox) {
        this.products = products;
        this.groups = groups;
        this.options = options;
        this.variants = variants;
        this.variantService = variantService;
        this.pricing = pricing;
        this.orderItems = orderItems;
        this.cacheEvictor = cacheEvictor;
        this.inbox = inbox;
    }

    @Transactional
    public Long create(CreateProductRequest req) {
        boolean hasPriceAffecting = nullSafe(req.groups()).stream().anyMatch(GroupInput::priceAffecting);
        PriceMode mode = hasPriceAffecting ? PriceMode.VARIANT : PriceMode.SINGLE;
        if (mode == PriceMode.SINGLE && req.singlePriceCents() == null) {
            throw badRequest("SINGLE_PRICE_REQUIRED");
        }

        String sku = (req.sku() != null && !req.sku().isBlank()) ? req.sku() : generateSku();
        if (products.existsBySku(sku)) throw badRequest("SKU_TAKEN");

        Product product = new Product(req.name(), uniqueSlug(slugify(req.name())), sku, mode);
        product.setParentProductId(req.parentProductId());
        product.setDescription(req.description());
        product.setDetails(req.details());
        product.setCategory(req.category());
        if (req.handlingClass() != null) product.setHandlingClass(HandlingClass.valueOf(req.handlingClass()));
        if (mode == PriceMode.SINGLE) {
            product.setSinglePriceCents(req.singlePriceCents());
            product.setStockQty(req.stockQty());
        }
        product = products.save(product);

        // Persist groups + options; collect price-affecting groups' (value -> optionId) maps in order.
        List<List<Long>> priceAffectingOptionIds = new ArrayList<>();
        List<Map<String, Long>> priceAffectingValueToId = new ArrayList<>();
        int groupOrder = 0;
        for (GroupInput g : nullSafe(req.groups())) {
            SpecGroup sg = groups.save(new SpecGroup(product.getId(), g.name(), g.priceAffecting(), groupOrder++));
            int optOrder = 0;
            List<Long> ids = new ArrayList<>();
            Map<String, Long> valueToId = new LinkedHashMap<>();
            for (String value : g.options()) {
                SpecOption so = options.save(new SpecOption(sg.getId(), value, optOrder++));
                ids.add(so.getId());
                valueToId.put(value, so.getId());
            }
            if (g.priceAffecting()) {
                priceAffectingOptionIds.add(ids);
                priceAffectingValueToId.add(valueToId);
            }
        }

        if (mode == PriceMode.VARIANT) {
            persistVariants(product, priceAffectingOptionIds, priceAffectingValueToId, nullSafe(req.variants()));
        }
        cacheEvictor.evictAll();
        if (!product.isArchived() && product.isActive()) {
            inbox.notifyNewProduct(product.getId(), product.getName(), product.getSlug());
        }
        return product.getId();
    }

    /** Partial update of product basics (FR-CATALOG-1); null fields unchanged. */
    @Transactional
    public void update(Long id, UpdateProductRequest req) {
        Product p = products.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found"));
        if (req.name() != null) p.setName(req.name());
        if (req.description() != null) p.setDescription(req.description());
        if (req.details() != null) p.setDetails(req.details());
        if (req.category() != null) p.setCategory(req.category());
        if (req.parentProductId() != null) p.setParentProductId(req.parentProductId());
        if (req.handlingClass() != null) p.setHandlingClass(HandlingClass.valueOf(req.handlingClass()));
        if (req.active() != null) p.setActive(req.active());
        if (req.displayOrder() != null) p.setDisplayOrder(req.displayOrder());
        if (p.getPriceMode() == PriceMode.SINGLE) {
            if (req.singlePriceCents() != null) p.setSinglePriceCents(req.singlePriceCents());
            if (req.stockQty() != null) {
                String before = StockAvailability.fromQty(p.getStockQty());
                p.setStockQty(req.stockQty());
                String after = StockAvailability.fromQty(req.stockQty());
                if ("OUT_OF_STOCK".equals(before) && !"OUT_OF_STOCK".equals(after)) {
                    inbox.notifyStockRestock(p.getId(), p.getName(), p.getSlug());
                }
            }
        }
        if (req.delivery() != null) applyDelivery(p, req.delivery());   // FR-CATALOG-14b/c
        products.save(p);
        cacheEvictor.evictAll();
    }

    /**
     * Set/replace a single variant's delivery attributes so the admin can change one size's weight or
     * per-zone lorry charge over time, without rebuilding the product (FR-CATALOG-14b/c, owner 2026-06-27).
     */
    @Transactional
    public void updateVariantDelivery(Long variantId, DeliveryAttrs d) {
        ProductVariant v = variants.findById(variantId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Variant not found"));
        applyDelivery(v, d);
        variants.save(v);
        cacheEvictor.evictAll();
    }

    /**
     * Change one existing variant's (size's) price (loosely-coupled admin editing, owner 2026-08-18 —
     * previously a size's price could only be set at creation; a typo or a supplier price change meant
     * deleting and re-adding the size, which is refused once it's been ordered).
     */
    @Transactional
    public void updateVariantPrice(Long variantId, long priceCents) {
        ProductVariant v = variants.findById(variantId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Variant not found"));
        v.setPriceCents(priceCents);
        variants.save(v);
        Product p = products.findById(v.getProductId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found"));
        recomputePriceRange(p);
        cacheEvictor.evictAll();
    }

    public record VariantView(long id, String sku, long priceCents, String signature) {}

    /**
     * Add one variant (size) to an existing VARIANT product (owner 2026-07-11 — sizes forgotten at
     * create time can now be added without rebuilding the product). One option value per price-affecting
     * group; a value that isn't a known option yet is created (e.g. a new "2 x 3" size). The product's
     * denormalised price range is recomputed.
     */
    @Transactional
    public VariantView addVariant(Long productId, List<String> optionValues, long priceCents, Integer stockQty) {
        Product p = products.findById(productId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found"));
        if (p.getPriceMode() != PriceMode.VARIANT) throw badRequest("NOT_A_VARIANT_PRODUCT");
        if (priceCents <= 0) throw badRequest("INVALID_PRICE");
        List<SpecGroup> priceGroups = groups.findByProductIdOrderByDisplayOrderAsc(productId).stream()
            .filter(SpecGroup::isPriceAffecting).toList();
        if (optionValues == null || optionValues.size() != priceGroups.size()) throw badRequest("VARIANT_OPTION_COUNT");

        List<Long> optionIds = new ArrayList<>();
        for (int i = 0; i < priceGroups.size(); i++) {
            SpecGroup g = priceGroups.get(i);
            String value = optionValues.get(i) == null ? "" : optionValues.get(i).trim();
            if (value.isEmpty()) throw badRequest("EMPTY_OPTION");
            SpecOption opt = options.findBySpecGroupIdAndValue(g.getId(), value).orElseGet(() ->
                options.save(new SpecOption(g.getId(), value, options.findBySpecGroupIdOrderByDisplayOrderAsc(g.getId()).size())));
            optionIds.add(opt.getId());
        }
        String signature = variantService.signature(optionIds);
        if (variants.findByProductIdAndOptionsSignature(productId, signature).isPresent())
            throw new ResponseStatusException(HttpStatus.CONFLICT, "VARIANT_EXISTS");

        ProductVariant v = variants.save(new ProductVariant(productId, generateSku(), priceCents, stockQty, signature));
        recomputePriceRange(p);
        cacheEvictor.evictAll();
        return new VariantView(v.getId(), v.getSku(), priceCents, signature);
    }

    /**
     * Delete one variant (size) from a VARIANT product. Refused if the size has ever been ordered (its
     * order history must stay intact — deactivate instead), or if it's the product's last variant.
     * Spec options left unused by the removal are cleaned up so no dangling, unpickable size remains.
     */
    @Transactional
    public void deleteVariant(Long productId, Long variantId) {
        Product p = products.findById(productId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found"));
        ProductVariant v = variants.findById(variantId)
            .filter(x -> x.getProductId().equals(productId))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Variant not found"));
        if (orderItems.existsByVariantId(variantId)) throw new ResponseStatusException(HttpStatus.CONFLICT, "VARIANT_ORDERED");
        if (variants.findByProductId(productId).size() <= 1) throw badRequest("LAST_VARIANT");

        Set<Long> removedOptionIds = signatureOptionIds(v.getOptionsSignature());
        variants.delete(v);
        variants.flush();
        Set<Long> stillUsed = variants.findByProductId(productId).stream()
            .flatMap(rv -> signatureOptionIds(rv.getOptionsSignature()).stream())
            .collect(Collectors.toSet());
        for (Long optId : removedOptionIds) {
            if (!stillUsed.contains(optId)) options.deleteById(optId);
        }
        recomputePriceRange(p);
        cacheEvictor.evictAll();
    }

    private void recomputePriceRange(Product p) {
        List<Long> prices = variants.findByProductId(p.getId()).stream()
            .filter(ProductVariant::isActive).map(ProductVariant::getPriceCents).toList();
        if (!prices.isEmpty()) {
            long[] range = pricing.priceRange(prices);
            p.setPriceRange(range[0], range[1]);
            products.save(p);
        }
    }

    private static Set<Long> signatureOptionIds(String signature) {
        Set<Long> ids = new java.util.HashSet<>();
        for (String tok : signature.split("-")) {
            if (!tok.isBlank()) ids.add(Long.parseLong(tok.trim()));
        }
        return ids;
    }

    /**
     * Product-level delivery-only update (single-priced products, no variants) — the lorry-pricing
     * overview (08/17, owner 2026-07-07) edits each row without resending the whole product payload.
     */
    @Transactional
    public void updateProductDelivery(Long productId, DeliveryAttrs d) {
        Product p = products.findById(productId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found"));
        applyDelivery(p, d);
        products.save(p);
        cacheEvictor.evictAll();
    }

    /** Apply the delivery block wholesale (nulls included, so a charge/gate can be cleared). */
    private static void applyDelivery(Product p, DeliveryAttrs d) {
        p.setBoardSizeTier(d.boardSizeTier());
        p.setWeightKg(d.weightKg());                                          // Domex weight rate (V48)
        p.setLorryColomboCents(d.lorryColomboCents());
        p.setLorrySuburbCents(d.lorrySuburbCents());
        p.setLorryOuterCents(d.lorryOuterCents());
        p.setLorryColomboGateCents(d.lorryColomboGateCents());
        p.setLorrySuburbGateCents(d.lorrySuburbGateCents());
        p.setLorryOuterGateCents(d.lorryOuterGateCents());
        p.setLorryColomboEnabled(!Boolean.FALSE.equals(d.lorryColomboEnabled()));  // null ⇒ on
        p.setLorrySuburbEnabled(!Boolean.FALSE.equals(d.lorrySuburbEnabled()));
        p.setLorryOuterEnabled(!Boolean.FALSE.equals(d.lorryOuterEnabled()));
        p.setLorryOuterWhatsapp(Boolean.TRUE.equals(d.lorryOuterWhatsapp()));
        p.setCourierOuterBlocked(Boolean.TRUE.equals(d.courierOuterBlocked()));
        p.setCourierEnabled(!Boolean.FALSE.equals(d.courierEnabled()));       // null ⇒ on
        p.setWhatsappOnly(Boolean.TRUE.equals(d.whatsappOnly()));
    }

    private static void applyDelivery(ProductVariant v, DeliveryAttrs d) {
        v.setBoardSizeTier(d.boardSizeTier());
        v.setWeightKg(d.weightKg());                                          // per-size courier weight
        v.setLorryColomboCents(d.lorryColomboCents());
        v.setLorrySuburbCents(d.lorrySuburbCents());
        v.setLorryOuterCents(d.lorryOuterCents());
        v.setLorryColomboGateCents(d.lorryColomboGateCents());
        v.setLorrySuburbGateCents(d.lorrySuburbGateCents());
        v.setLorryOuterGateCents(d.lorryOuterGateCents());
        v.setLorryColomboEnabled(!Boolean.FALSE.equals(d.lorryColomboEnabled()));
        v.setLorrySuburbEnabled(!Boolean.FALSE.equals(d.lorrySuburbEnabled()));
        v.setLorryOuterEnabled(!Boolean.FALSE.equals(d.lorryOuterEnabled()));
        v.setLorryOuterWhatsapp(Boolean.TRUE.equals(d.lorryOuterWhatsapp()));
        v.setCourierOuterBlocked(Boolean.TRUE.equals(d.courierOuterBlocked()));
        v.setCourierEnabled(!Boolean.FALSE.equals(d.courierEnabled()));
        v.setWhatsappOnly(Boolean.TRUE.equals(d.whatsappOnly()));
    }

    /** Toggle storefront visibility without deleting (FR-CATALOG-12). */
    @Transactional
    public void setActive(Long id, boolean active) {
        Product p = products.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found"));
        p.setActive(active);
        products.save(p);
        cacheEvictor.evictAll();
    }

    /** Preview variant combinations for admin UI (01 FR-CATALOG-6). */
    public VariantPreviewResponse previewVariants(List<GroupInput> groups) {
        List<List<String>> priceGroups = nullSafe(groups).stream()
            .filter(GroupInput::priceAffecting)
            .map(GroupInput::options)
            .toList();
        List<List<String>> combos = variantService.cartesianValues(priceGroups);
        List<VariantPreviewRowDTO> rows = new ArrayList<>();
        for (int i = 0; i < combos.size(); i++) {
            rows.add(new VariantPreviewRowDTO(combos.get(i), i));
        }
        return new VariantPreviewResponse(rows);
    }

    /**
     * Delete a product (FR-CATALOG-10). Hard-delete when never ordered; soft-archive otherwise.
     */
    @Transactional
    public String delete(Long id) {
        Product p = products.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found"));
        if (orderItems.existsByProductId(id)) {
            p.archive();
            p.setActive(false);
            products.save(p);
            cacheEvictor.evictAll();
            return "ARCHIVED";
        }
        products.delete(p);
        cacheEvictor.evictAll();
        return "DELETED";
    }

    private void persistVariants(Product product, List<List<Long>> optionIdGroups,
                                 List<Map<String, Long>> valueToIdPerGroup, List<VariantInput> variantInputs) {
        List<List<Long>> combos = variantService.cartesian(optionIdGroups);
        Set<String> expected = combos.stream().map(variantService::signature).collect(Collectors.toSet());

        Map<String, VariantInput> provided = new HashMap<>();
        for (VariantInput vi : variantInputs) {
            if (vi.optionValues().size() != valueToIdPerGroup.size()) throw badRequest("VARIANT_OPTION_COUNT");
            List<Long> ids = new ArrayList<>();
            for (int i = 0; i < vi.optionValues().size(); i++) {
                Long id = valueToIdPerGroup.get(i).get(vi.optionValues().get(i));
                if (id == null) throw badRequest("UNKNOWN_OPTION");
                ids.add(id);
            }
            provided.put(variantService.signature(ids), vi);
        }
        pricing.assertCoverage(expected, provided.keySet());   // exactly the cartesian set

        List<Long> prices = new ArrayList<>();
        for (List<Long> combo : combos) {
            String sig = variantService.signature(combo);
            VariantInput vi = provided.get(sig);
            String vsku = (vi.sku() != null && !vi.sku().isBlank()) ? vi.sku() : generateSku();
            variants.save(new ProductVariant(product.getId(), vsku, vi.priceCents(), vi.stockQty(), sig));
            prices.add(vi.priceCents());
        }
        long[] range = pricing.priceRange(prices);
        product.setPriceRange(range[0], range[1]);
        products.save(product);
    }

    // --- helpers ---

    private static <T> List<T> nullSafe(List<T> list) {
        return list == null ? List.of() : list;
    }

    private String uniqueSlug(String base) {
        String slug = base;
        int n = 2;
        while (products.existsBySlug(slug)) {
            slug = base + "-" + n++;
        }
        return slug;
    }

    private static String slugify(String name) {
        String slug = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
        return slug.isBlank() ? "product" : slug;
    }

    private static String generateSku() {
        return "SL-" + Long.toString(Math.abs(RNG.nextLong()), 36).toUpperCase(Locale.ROOT);
    }

    private static ResponseStatusException badRequest(String code) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, code);
    }
}
