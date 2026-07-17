package com.scanlanka.checkout.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Domex weight-based courier rates per area (owner 2026-07-16 rate card, V48). Per board:
 * first kg + (ceil(weight) - 1) × additional kg; a package above 2 ft
 * ({@link BoardSizeTier} other than {@code UNDER_2FT}) adds the area's per-package handling fee.
 * Admin-editable (08). Money in integer LKR cents.
 */
@Entity
@Table(name = "courier_rate_card")
public class CourierRateCard {

    @Id
    @Enumerated(EnumType.STRING)
    @Column(name = "zone", nullable = false)
    private CourierZone zone;

    @Column(name = "first_kg_cents", nullable = false)
    private long firstKgCents;

    @Column(name = "addl_kg_cents", nullable = false)
    private long addlKgCents;

    @Column(name = "handling_over_2ft_cents", nullable = false)
    private long handlingOver2ftCents;

    protected CourierRateCard() {}

    public CourierRateCard(CourierZone zone, long firstKgCents, long addlKgCents, long handlingOver2ftCents) {
        this.zone = zone;
        this.firstKgCents = firstKgCents;
        this.addlKgCents = addlKgCents;
        this.handlingOver2ftCents = handlingOver2ftCents;
    }

    public CourierZone getZone() { return zone; }
    public long getFirstKgCents() { return firstKgCents; }
    public long getAddlKgCents() { return addlKgCents; }
    public long getHandlingOver2ftCents() { return handlingOver2ftCents; }

    public void update(long firstKgCents, long addlKgCents, long handlingOver2ftCents) {
        this.firstKgCents = firstKgCents;
        this.addlKgCents = addlKgCents;
        this.handlingOver2ftCents = handlingOver2ftCents;
    }
}
