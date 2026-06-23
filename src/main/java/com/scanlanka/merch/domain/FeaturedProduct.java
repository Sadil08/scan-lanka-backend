package com.scanlanka.merch.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "featured_product")
public class FeaturedProduct {

    @Id
    @Column(name = "product_id")
    private Long productId;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    protected FeaturedProduct() {}

    public FeaturedProduct(Long productId, int displayOrder) {
        this.productId = productId;
        this.displayOrder = displayOrder;
    }

    public Long getProductId() { return productId; }
    public int getDisplayOrder() { return displayOrder; }
}
