package com.scanlanka.catalog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

/**
 * A purchasable product. SINGLE-priced (single_price_cents) or VARIANT-priced (variants carry price;
 * price_range_* denormalized for chips). Stock per-variant when variants exist else here (FR-CATALOG-9).
 * Money in integer LKR cents.
 */
@Entity
@Table(name = "product")
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "parent_product_id")
    private Long parentProductId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String slug;

    @Column(nullable = false, unique = true)
    private String sku;

    @Column(columnDefinition = "text")
    private String description;

    @Column(columnDefinition = "text")
    private String details;

    private String category;

    @Enumerated(EnumType.STRING)
    @Column(name = "price_mode", nullable = false)
    private PriceMode priceMode;

    @Column(name = "single_price_cents")
    private Long singlePriceCents;

    @Column(name = "price_range_min_cents")
    private Long priceRangeMinCents;

    @Column(name = "price_range_max_cents")
    private Long priceRangeMaxCents;

    @Column(name = "stock_qty")
    private Integer stockQty;          // null = unlimited

    @Enumerated(EnumType.STRING)
    @Column(name = "handling_class", nullable = false)
    private HandlingClass handlingClass = HandlingClass.STANDARD;

    @Column(nullable = false)
    private boolean active = true;

    @Column(nullable = false)
    private boolean archived = false;

    @Column(name = "archived_at")
    private Instant archivedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Product() {}

    public Product(String name, String slug, String sku, PriceMode priceMode) {
        this.name = name;
        this.slug = slug;
        this.sku = sku;
        this.priceMode = priceMode;
    }

    public boolean isVariantPriced() {
        return priceMode == PriceMode.VARIANT;
    }

    public void archive() {
        this.archived = true;
        this.archivedAt = Instant.now();
    }

    public Long getId() { return id; }
    public Long getParentProductId() { return parentProductId; }
    public void setParentProductId(Long parentProductId) { this.parentProductId = parentProductId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getSlug() { return slug; }
    public String getSku() { return sku; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getDetails() { return details; }
    public void setDetails(String details) { this.details = details; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public PriceMode getPriceMode() { return priceMode; }
    public Long getSinglePriceCents() { return singlePriceCents; }
    public void setSinglePriceCents(Long singlePriceCents) { this.singlePriceCents = singlePriceCents; }
    public Long getPriceRangeMinCents() { return priceRangeMinCents; }
    public Long getPriceRangeMaxCents() { return priceRangeMaxCents; }
    public void setPriceRange(Long minCents, Long maxCents) {
        this.priceRangeMinCents = minCents;
        this.priceRangeMaxCents = maxCents;
    }
    public Integer getStockQty() { return stockQty; }
    public void setStockQty(Integer stockQty) { this.stockQty = stockQty; }
    public HandlingClass getHandlingClass() { return handlingClass; }
    public void setHandlingClass(HandlingClass handlingClass) { this.handlingClass = handlingClass; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public boolean isArchived() { return archived; }
    public Instant getUpdatedAt() { return updatedAt; }
}
