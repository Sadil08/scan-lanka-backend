package com.scanlanka.returns.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "refund_item")
public class RefundItem {

    public enum Disposition { RESTOCK, WRITE_OFF }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "refund_id", nullable = false)
    private Long refundId;
    @Column(name = "order_item_id", nullable = false)
    private Long orderItemId;
    @Column(nullable = false)
    private int quantity;
    @Column(nullable = false)
    private String disposition;

    protected RefundItem() {}

    public RefundItem(Long refundId, Long orderItemId, int quantity, Disposition disposition) {
        this.refundId = refundId;
        this.orderItemId = orderItemId;
        this.quantity = quantity;
        this.disposition = disposition.name();
    }

    public Long getRefundId() { return refundId; }
    public Long getOrderItemId() { return orderItemId; }
    public int getQuantity() { return quantity; }
    public String getDisposition() { return disposition; }
}
