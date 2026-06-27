package com.scanlanka.checkout.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Global delivery settings (singleton): the in-house-lorry minimum bill (owner: "greater than Rs 6,000"). */
@Entity
@Table(name = "delivery_settings")
public class DeliverySettings {

    @Id
    private Short id;

    @Column(name = "lorry_min_bill_cents", nullable = false)
    private long lorryMinBillCents;

    protected DeliverySettings() {}

    public Short getId() { return id; }
    public long getLorryMinBillCents() { return lorryMinBillCents; }
    public void setLorryMinBillCents(long lorryMinBillCents) { this.lorryMinBillCents = lorryMinBillCents; }
}
