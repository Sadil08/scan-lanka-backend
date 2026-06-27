package com.scanlanka.checkout.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Citrek per-zone rate: estimate = round(weight × per_kg) + base. Admin-editable (08). */
@Entity
@Table(name = "courier_rate_card")
public class CourierRateCard {

    @Id
    @Enumerated(EnumType.STRING)
    private CourierZone zone;

    @Column(name = "base_cents", nullable = false)
    private long baseCents;

    @Column(name = "per_kg_cents", nullable = false)
    private long perKgCents;

    protected CourierRateCard() {}

    public CourierRateCard(CourierZone zone, long baseCents, long perKgCents) {
        this.zone = zone;
        this.baseCents = baseCents;
        this.perKgCents = perKgCents;
    }

    public CourierZone getZone() { return zone; }
    public long getBaseCents() { return baseCents; }
    public long getPerKgCents() { return perKgCents; }

    public void update(long baseCents, long perKgCents) {
        this.baseCents = baseCents;
        this.perKgCents = perKgCents;
    }
}
