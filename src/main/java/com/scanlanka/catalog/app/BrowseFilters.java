package com.scanlanka.catalog.app;

/** Public product list filters (02-storefront-browse §3). */
public record BrowseFilters(String q, Long parentId, String category, String sort) {

    public String normalizedSort() {
        if (sort == null || sort.isBlank()) return "newest";
        return switch (sort) {
            case "newest", "price_asc", "price_desc", "name" -> sort;
            default -> "newest";
        };
    }

    public String cacheKey() {
        return (q == null ? "" : q.trim().toLowerCase()) + "|"
            + (parentId == null ? "" : parentId) + "|"
            + (category == null ? "" : category.trim()) + "|"
            + normalizedSort();
    }
}
