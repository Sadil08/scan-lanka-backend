package com.scanlanka.catalog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** A product image. One preview per product (DB partial unique index); ordered gallery (FR-CATALOG-2). */
@Entity
@Table(name = "product_image")
public class ProductImage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "storage_key", nullable = false)
    private String storageKey;

    @Column(nullable = false)
    private String url;

    @Column(name = "alt_text")
    private String altText;

    @Column(name = "is_preview", nullable = false)
    private boolean preview;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    protected ProductImage() {}

    public ProductImage(Long productId, String storageKey, String url, boolean preview, int displayOrder) {
        this.productId = productId;
        this.storageKey = storageKey;
        this.url = url;
        this.preview = preview;
        this.displayOrder = displayOrder;
    }

    public Long getId() { return id; }
    public Long getProductId() { return productId; }
    public String getStorageKey() { return storageKey; }
    public String getUrl() { return url; }
    public String getAltText() { return altText; }
    public void setAltText(String altText) { this.altText = altText; }
    public boolean isPreview() { return preview; }
    public void setPreview(boolean preview) { this.preview = preview; }
    public int getDisplayOrder() { return displayOrder; }
    public void setDisplayOrder(int displayOrder) { this.displayOrder = displayOrder; }
}
