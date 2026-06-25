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

    public record SpecGroupDTO(long id, String name, boolean priceAffecting, List<OptionDTO> options) {}

    public record VariantDTO(long id, String sku, long priceCents, String optionsSignature, String availability) {}

    public record ProductDetailDTO(
        long id, String slug, String name, String description, String details,
        String priceMode, Long singlePriceCents, Long priceMinCents, Long priceMaxCents,
        String availability,
        List<String> imageUrls, List<SpecGroupDTO> specGroups, List<VariantDTO> variants) {}

    public record ParentFacetDTO(long id, String name, String slug) {}

    public record CatalogFacetsDTO(List<ParentFacetDTO> parents, List<String> categories) {}

    public record CategoryCountDTO(String name, long count) {}

    public record VariantPreviewRowDTO(List<String> optionValues, int index) {}

    public record VariantPreviewResponse(List<VariantPreviewRowDTO> rows) {}

    public record ResolveVariantRequest(List<Long> selectedOptionIds) {}

    public record ResolveVariantResponse(long variantId, String sku, long priceCents, String availability) {}

    /** Admin product list row (01 §3 — includes hidden/archived). */
    public record AdminProductRowDTO(
        long id, String name, String slug, String sku, String category,
        String priceMode, boolean active, boolean archived,
        Integer stockQty, Long singlePriceCents, Long priceMinCents, Long priceMaxCents,
        String previewImageUrl) {}

    /** Admin product detail for edit form (01 §3). */
    public record AdminProductDetailDTO(
        long id, String name, String slug, String sku, String description, String details,
        String category, String handlingClass, Long parentProductId,
        boolean active, boolean archived, String priceMode,
        Long singlePriceCents, Integer stockQty,
        List<String> imageUrls, List<SpecGroupDTO> specGroups, List<VariantDTO> variants) {}

    public record CategoryAdminDTO(String name, long productCount) {}
}
