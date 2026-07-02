package com.scanlanka.checkout.domain;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** Citrek flat rate per courier zone + board size tier. Admin-editable (08). */
@Entity
@Table(name = "courier_rate_card")
public class CourierRateCard {

    @EmbeddedId
    private CourierRateCardId id;

    @Column(name = "flat_cents", nullable = false)
    private long flatCents;

    protected CourierRateCard() {}

    public CourierRateCard(CourierZone zone, BoardSizeTier sizeTier, long flatCents) {
        this.id = new CourierRateCardId(zone, sizeTier);
        this.flatCents = flatCents;
    }

    public CourierRateCardId getId() { return id; }
    public CourierZone getZone() { return id.getZone(); }
    public BoardSizeTier getSizeTier() { return id.getSizeTier(); }
    public long getFlatCents() { return flatCents; }

    public void update(long flatCents) {
        this.flatCents = flatCents;
    }
}
