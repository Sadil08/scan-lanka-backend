package com.scanlanka.catalog.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

/** Admin product create/upsert DTOs (01-product-catalog §3). Strict validation (global/02 §4). */
public final class ProductRequests {

    private ProductRequests() {}

    /**
     * Two-rail delivery attributes (01 FR-CATALOG-14, 17). Set on the product (defaults) or per variant
     * (per size). A null `lorry_*_cents` means "not lorried into that zone" (the lorry is still offered —
     * admin arranges far cost manually). The Rs 6,000 lorry minimum is global (`delivery_settings`, 08).
     */
    public record DeliveryAttrs(
        @PositiveOrZero BigDecimal weightKg,                 // null ⇒ not couriable
        @PositiveOrZero Long lorryColomboCents,
        @PositiveOrZero Long lorrySuburbCents,
        @PositiveOrZero Long lorryOuterCents,
        Boolean whatsappOnly) {}

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

    /** Set/replace the per-size (or product-level) delivery attributes (FR-CATALOG-14b/c). */
    public record DeliveryUpdateRequest(@Valid DeliveryAttrs delivery) {}

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
        Long singlePriceCents,      // SINGLE only
        @Valid DeliveryAttrs delivery) {}   // when present, replaces the product's delivery defaults

    public record ActiveRequest(boolean active) {}

    public record RenameCategoryRequest(
        @NotBlank @Size(max = 120) String from,
        @NotBlank @Size(max = 120) String to) {}
}
