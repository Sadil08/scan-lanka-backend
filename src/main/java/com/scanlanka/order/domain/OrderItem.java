package com.scanlanka.order.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** A snapshotted order line — survives product edit/delete (09 FR-ORDER-1/8). */
@Entity
@Table(name = "order_item")
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "product_id")
    private Long productId;
    @Column(name = "variant_id")
    private Long variantId;

    @Column(name = "sku_snapshot", nullable = false)
    private String skuSnapshot;
    @Column(name = "name_snapshot", nullable = false)
    private String nameSnapshot;
    @Column(name = "handling_class_snapshot")
    private String handlingClassSnapshot;

    @Column(name = "unit_price_cents", nullable = false)
    private long unitPriceCents;
    @Column(nullable = false)
    private int quantity;
    @Column(name = "line_total_cents", nullable = false)
    private long lineTotalCents;

    protected OrderItem() {}

    public OrderItem(Long orderId, Long productId, Long variantId, String sku, String name,
                     String handlingClass, long unitPriceCents, int quantity, long lineTotalCents) {
        this.orderId = orderId;
        this.productId = productId;
        this.variantId = variantId;
        this.skuSnapshot = sku;
        this.nameSnapshot = name;
        this.handlingClassSnapshot = handlingClass;
        this.unitPriceCents = unitPriceCents;
        this.quantity = quantity;
        this.lineTotalCents = lineTotalCents;
    }

    public Long getId() { return id; }
    public Long getOrderId() { return orderId; }
    public String getSkuSnapshot() { return skuSnapshot; }
    public String getNameSnapshot() { return nameSnapshot; }
    public long getUnitPriceCents() { return unitPriceCents; }
    public int getQuantity() { return quantity; }
    public long getLineTotalCents() { return lineTotalCents; }
}
