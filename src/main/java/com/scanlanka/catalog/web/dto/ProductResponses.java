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
        List<String> imageUrls, List<SpecGroupDTO> specGroups, List<VariantDTO> variants) {}

    public record ResolveVariantRequest(List<Long> selectedOptionIds) {}

    public record ResolveVariantResponse(long variantId, String sku, long priceCents, String availability) {}
}
