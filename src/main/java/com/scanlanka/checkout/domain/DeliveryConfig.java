package com.scanlanka.checkout.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Global delivery-engine config (singleton row id=1). */
@Entity
@Table(name = "delivery_config")
public class DeliveryConfig {

    @Id
    private Integer id;

    @Column(name = "pick_first_cents", nullable = false)
    private long pickFirstCents;
    @Column(name = "pick_next_cents", nullable = false)
    private long pickNextCents;
    @Column(name = "fragile_surcharge_cents", nullable = false)
    private long fragileSurchargeCents;
    @Column(name = "oversize_surcharge_cents", nullable = false)
    private long oversizeSurchargeCents;
    @Column(name = "dim_divisor", nullable = false)
    private int dimDivisor;

    protected DeliveryConfig() {}

    public long getPickFirstCents() { return pickFirstCents; }
    public long getPickNextCents() { return pickNextCents; }
    public long getFragileSurchargeCents() { return fragileSurchargeCents; }
    public long getOversizeSurchargeCents() { return oversizeSurchargeCents; }
    public int getDimDivisor() { return dimDivisor; }

    public void update(long pickFirst, long pickNext, long fragile, long oversize, int dimDivisor) {
        this.pickFirstCents = pickFirst;
        this.pickNextCents = pickNext;
        this.fragileSurchargeCents = fragile;
        this.oversizeSurchargeCents = oversize;
        this.dimDivisor = dimDivisor;
    }
}
