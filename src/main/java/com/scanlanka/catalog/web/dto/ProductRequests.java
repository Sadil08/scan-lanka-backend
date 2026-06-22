package com.scanlanka.catalog.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.util.List;

/** Admin product create/upsert DTOs (01-product-catalog §3). Strict validation (global/02 §4). */
public final class ProductRequests {

    private ProductRequests() {}

    public record GroupInput(
        @NotBlank @Size(max = 120) String name,
        boolean priceAffecting,
        @NotEmpty List<@NotBlank String> options) {}

    /** A variant's chosen option (one value per price-affecting group, in group order) + its price. */
    public record VariantInput(
        @NotEmpty List<@NotBlank String> optionValues,
        @PositiveOrZero long priceCents,
        String sku,
        Integer stockQty) {}

    public record CreateProductRequest(
        Long parentProductId,
        @NotBlank @Size(max = 200) String name,
        String sku,
        String description,
        String details,
        String category,
        String handlingClass,
        Integer stockQty,           // product-level (used for SINGLE; null = unlimited)
        Long singlePriceCents,      // required when no price-affecting group (SINGLE)
        @Valid List<GroupInput> groups,
        @Valid List<VariantInput> variants) {}

    /** Partial update of product basics (null fields are left unchanged). Spec rebuild is a separate flow. */
    public record UpdateProductRequest(
        @Size(max = 200) String name,
        String description,
        String details,
        String category,
        Long parentProductId,
        String handlingClass,
        Boolean active,
        Integer stockQty,           // SINGLE only
        Long singlePriceCents) {}   // SINGLE only

    public record ActiveRequest(boolean active) {}
}
