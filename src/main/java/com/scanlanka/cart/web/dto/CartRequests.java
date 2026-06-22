package com.scanlanka.cart.web.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/** Cart request DTOs. Strict input typing (global/02 §4); client never sends prices (SEC-CART-2). */
public final class CartRequests {

    private CartRequests() {}

    public record ItemDTO(@NotNull Long productId, Long variantId, @Min(1) int quantity) {}

    public record CartItemsRequest(List<ItemDTO> items) {}

    public record AddItemRequest(@NotNull Long productId, Long variantId, @Min(1) int quantity) {}

    public record QuantityRequest(@Min(1) int quantity) {}
}
