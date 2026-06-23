package com.scanlanka.quote.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "quote_item")
public class QuoteItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "quote_id", nullable = false)
    private Long quoteId;
    @Column(name = "product_id")
    private Long productId;
    @Column(name = "variant_id")
    private Long variantId;
    @Column(name = "name_snapshot", nullable = false)
    private String nameSnapshot;
    @Column(nullable = false)
    private int quantity;
    private String note;

    protected QuoteItem() {}

    public QuoteItem(Long quoteId, Long productId, Long variantId, String nameSnapshot, int quantity, String note) {
        this.quoteId = quoteId;
        this.productId = productId;
        this.variantId = variantId;
        this.nameSnapshot = nameSnapshot;
        this.quantity = quantity;
        this.note = note;
    }

    public Long getId() { return id; }
    public Long getQuoteId() { return quoteId; }
    public Long getProductId() { return productId; }
    public Long getVariantId() { return variantId; }
    public String getNameSnapshot() { return nameSnapshot; }
    public int getQuantity() { return quantity; }
    public String getNote() { return note; }
}
