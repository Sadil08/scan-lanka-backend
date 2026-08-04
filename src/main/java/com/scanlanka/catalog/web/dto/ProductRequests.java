package com.scanlanka.catalog.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import com.scanlanka.checkout.domain.BoardSizeTier;

import java.math.BigDecimal;
import java.util.List;

/** Admin product create/upsert DTOs (01-product-catalog §3). Strict validation (global/02 §4). */
public final class ProductRequests {

    private ProductRequests() {}

    /**
     * Two-rail delivery attributes (01 FR-CATALOG-14, 17, owner 2026-07-05 — fully admin-controlled).
     * Set on the product (defaults) or per variant (per size). Per zone: an enabled switch (null ⇒ true),
     * an optional price, and an optional min-bill gate. Blank price + no gate = lorry offered, admin
     * arranges the cost manually; gate with no price = the zone's flat gate-met charge when met.
     *
     * <p>{@code weightKg} drives the Domex courier charge (V48 weight rate: first kg + additional kgs)
     * and is loosely coupled like the lorry cells — set per size on a variant-priced product, on the
     * product itself for a single-priced one; a variant without its own weight falls back to the
     * product's. Null is allowed but bills as 1 kg, so an item with the courier ON should carry one.
     */
    public record DeliveryAttrs(
        BoardSizeTier boardSizeTier,                         // null ⇒ not couriable (lorry-only item)
        @DecimalMin("0") @Digits(integer = 7, fraction = 3) BigDecimal weightKg,  // null ⇒ billed as 1 kg
        @PositiveOrZero Long lorryColomboCents,
        @PositiveOrZero Long lorrySuburbCents,
        @PositiveOrZero Long lorryOuterCents,
        @PositiveOrZero Long lorryColomboGateCents,          // min bill per zone (null ⇒ no gate)
        @PositiveOrZero Long lorrySuburbGateCents,
        @PositiveOrZero Long lorryOuterGateCents,
        Boolean lorryColomboEnabled,                         // null ⇒ true (zone on)
        Boolean lorrySuburbEnabled,
        Boolean lorryOuterEnabled,
        Boolean lorryOuterWhatsapp,                          // outer lorry = contact us (glass, big boards)
        Boolean courierOuterBlocked,                         // no courier to outer Domex areas (6×3/6×4/8×4)
        Boolean courierEnabled,                              // null ⇒ true (courier offered for this item)
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

    /** Add one variant (size) to an existing variant product: option value per price-affecting group. */
    public record AddVariantRequest(
        @NotEmpty List<@NotBlank String> optionValues,
        @Positive long priceCents,
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
        Integer displayOrder,       // storefront position (owner sheet order, V46); null unchanged
        @Valid DeliveryAttrs delivery) {}   // when present, replaces the product's delivery defaults

    public record ActiveRequest(boolean active) {}

    public record RenameCategoryRequest(
        @NotBlank @Size(max = 120) String from,
        @NotBlank @Size(max = 120) String to) {}
}
