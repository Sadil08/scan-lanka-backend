package com.scanlanka.catalog.web.dto;

import java.util.List;

/** Storefront read projections (02-storefront-browse §2). Prices server-supplied (LKR cents). */
public final class ProductResponses {

    private ProductResponses() {}

    public record ProductChipDTO(
        long id, String slug, String name, String previewImageUrl,
        String priceMode, Long priceCents, Long priceMinCents, Long priceMaxCents,
        String availability) {}

    public record OptionDTO(long id, String value) {}

    /** {@code variantId} null ⇒ product-level default image, shown when no size-specific image matches. */
    public record ImageDTO(String url, Long variantId) {}

    public record SpecGroupDTO(long id, String name, boolean priceAffecting, List<OptionDTO> options) {}

    public record VariantDTO(long id, String sku, long priceCents, String optionsSignature, String availability) {}

    /** {@code weightKg} drives the Domex weight rate (V48); null ⇒ the courier bills it as 1 kg. */
    public record DeliveryAttrsDTO(
        String boardSizeTier,
        java.math.BigDecimal weightKg,
        Long lorryColomboCents, Long lorrySuburbCents, Long lorryOuterCents,
        Long lorryColomboGateCents, Long lorrySuburbGateCents, Long lorryOuterGateCents,
        boolean lorryColomboEnabled, boolean lorrySuburbEnabled, boolean lorryOuterEnabled,
        boolean lorryOuterWhatsapp, boolean courierOuterBlocked,
        boolean courierEnabled,
        boolean whatsappOnly) {}

    public record AdminVariantDTO(
        long id, String sku, long priceCents, String optionsSignature, String availability,
        DeliveryAttrsDTO delivery) {}

    public record ProductDetailDTO(
        long id, String slug, String name, String description, String details,
        String category, Long parentProductId,
        String priceMode, Long singlePriceCents, Long priceMinCents, Long priceMaxCents,
        String availability,
        List<ImageDTO> imageUrls, List<SpecGroupDTO> specGroups, List<VariantDTO> variants,
        boolean whatsappOnly, String boardSizeTier, boolean couriable) {}

    public record ParentFacetDTO(long id, String name, String slug) {}

    public record CatalogFacetsDTO(List<ParentFacetDTO> parents, List<String> categories) {}

    /** group: top-level storefront group ("Writing Boards" … "Portable Partition"), null = top-level. */
    public record CategoryCountDTO(String name, long count, String group) {}

    /** Product link for the Our Products nav (single-category groups). */
    public record NavProductLinkDTO(String slug, String name) {}

    public record NavCategoryLinkDTO(String name, long count) {}

    /** One top-level nav group with categories and optional expanded product links. */
    public record NavMenuGroupDTO(String name, List<NavCategoryLinkDTO> categories, List<NavProductLinkDTO> products) {}

    public record VariantPreviewRowDTO(List<String> optionValues, int index) {}

    public record VariantPreviewResponse(List<VariantPreviewRowDTO> rows) {}

    public record ResolveVariantRequest(List<Long> selectedOptionIds) {}

    public record ResolveVariantResponse(long variantId, String sku, long priceCents, String availability,
        boolean whatsappOnly, String boardSizeTier, boolean couriable) {}

    /** Admin product list row (01 §3 — includes hidden/archived). */
    public record AdminProductRowDTO(
        long id, String name, String slug, String sku, String category,
        String priceMode, boolean active, boolean archived,
        Integer stockQty, Long singlePriceCents, Long priceMinCents, Long priceMaxCents,
        String previewImageUrl) {}

    /**
     * One purchasable unit's delivery attributes for the lorry-pricing overview (08/17, owner
     * 2026-07-07) — one row per product (single-priced) or per active variant (variant-priced),
     * flattened across the whole catalog so every size's lorry cell is visible/editable in one table.
     */
    public record LorryPricingRowDTO(
        long productId, String productName, Long variantId, String sizeLabel, DeliveryAttrsDTO delivery) {}

    /** Admin product detail for edit form (01 §3). */
    public record AdminProductDetailDTO(
        long id, String name, String slug, String sku, String description, String details,
        String category, String handlingClass, Long parentProductId,
        boolean active, boolean archived, String priceMode,
        Long singlePriceCents, Integer stockQty, int displayOrder,
        DeliveryAttrsDTO delivery,
        List<String> imageUrls, List<SpecGroupDTO> specGroups, List<AdminVariantDTO> variants) {}

    public record CategoryAdminDTO(String name, long productCount) {}
}
