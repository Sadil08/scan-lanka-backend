package com.scanlanka.wishlist.web.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;

public final class WishlistRequests {

    private WishlistRequests() {}

    public record MergeRequest(@NotNull List<Long> productIds) {}
}
