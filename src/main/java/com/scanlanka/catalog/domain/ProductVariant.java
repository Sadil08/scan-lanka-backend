package com.scanlanka.catalog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * A priced variant = one combination of the price-affecting options. `options_signature` (sorted
 * option ids) is unique per product and the resolution key from a cart selection (FR-CATALOG-6/13).
 */
@Entity
@Table(name = "product_variant")
public class ProductVariant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(nullable = false, unique = true)
    private String sku;

    @Column(name = "price_cents", nullable = false)
    private long priceCents;

    @Column(name = "stock_qty")
    private Integer stockQty;          // null = unlimited

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "options_signature", nullable = false)
    private String optionsSignature;

    // Per-size delivery attributes (05/17). Null falls back to the product-level default.
    @jakarta.persistence.Enumerated(jakarta.persistence.EnumType.STRING)
    @Column(name = "board_size_tier")
    private com.scanlanka.checkout.domain.BoardSizeTier boardSizeTier;
    @Column(name = "weight_kg")
    private java.math.BigDecimal weightKg;
    @Column(name = "lorry_colombo_cents")
    private Long lorryColomboCents;
    @Column(name = "lorry_suburb_cents")
    private Long lorrySuburbCents;
    @Column(name = "lorry_outer_cents")
    private Long lorryOuterCents;
    @Column(name = "whatsapp_only", nullable = false)
    private boolean whatsappOnly = false;

    protected ProductVariant() {}

    public ProductVariant(Long productId, String sku, long priceCents, Integer stockQty, String optionsSignature) {
        this.productId = productId;
        this.sku = sku;
        this.priceCents = priceCents;
        this.stockQty = stockQty;
        this.optionsSignature = optionsSignature;
    }

    public Long getId() { return id; }
    public Long getProductId() { return productId; }
    public String getSku() { return sku; }
    public long getPriceCents() { return priceCents; }
    public void setPriceCents(long priceCents) { this.priceCents = priceCents; }
    public Integer getStockQty() { return stockQty; }
    public void setStockQty(Integer stockQty) { this.stockQty = stockQty; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public String getOptionsSignature() { return optionsSignature; }
    public com.scanlanka.checkout.domain.BoardSizeTier getBoardSizeTier() { return boardSizeTier; }
    public void setBoardSizeTier(com.scanlanka.checkout.domain.BoardSizeTier boardSizeTier) {
        this.boardSizeTier = boardSizeTier;
    }
    public java.math.BigDecimal getWeightKg() { return weightKg; }
    public void setWeightKg(java.math.BigDecimal weightKg) { this.weightKg = weightKg; }
    public Long getLorryColomboCents() { return lorryColomboCents; }
    public void setLorryColomboCents(Long c) { this.lorryColomboCents = c; }
    public Long getLorrySuburbCents() { return lorrySuburbCents; }
    public void setLorrySuburbCents(Long c) { this.lorrySuburbCents = c; }
    public Long getLorryOuterCents() { return lorryOuterCents; }
    public void setLorryOuterCents(Long c) { this.lorryOuterCents = c; }
    public boolean isWhatsappOnly() { return whatsappOnly; }
    public void setWhatsappOnly(boolean whatsappOnly) { this.whatsappOnly = whatsappOnly; }
}
