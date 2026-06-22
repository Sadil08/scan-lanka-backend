package com.scanlanka.catalog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** An admin-named spec group (e.g. Size, Frame, Thickness); price-affecting or informational (FR-CATALOG-4/5). */
@Entity
@Table(name = "spec_group")
public class SpecGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(nullable = false)
    private String name;

    @Column(name = "price_affecting", nullable = false)
    private boolean priceAffecting;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    protected SpecGroup() {}

    public SpecGroup(Long productId, String name, boolean priceAffecting, int displayOrder) {
        this.productId = productId;
        this.name = name;
        this.priceAffecting = priceAffecting;
        this.displayOrder = displayOrder;
    }

    public Long getId() { return id; }
    public Long getProductId() { return productId; }
    public String getName() { return name; }
    public boolean isPriceAffecting() { return priceAffecting; }
    public int getDisplayOrder() { return displayOrder; }
}
