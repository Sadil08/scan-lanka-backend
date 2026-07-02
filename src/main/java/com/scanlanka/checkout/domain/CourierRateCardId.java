package com.scanlanka.checkout.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class CourierRateCardId implements Serializable {

    @Enumerated(EnumType.STRING)
    @Column(name = "zone", nullable = false)
    private CourierZone zone;

    @Enumerated(EnumType.STRING)
    @Column(name = "size_tier", nullable = false)
    private BoardSizeTier sizeTier;

    protected CourierRateCardId() {}

    public CourierRateCardId(CourierZone zone, BoardSizeTier sizeTier) {
        this.zone = zone;
        this.sizeTier = sizeTier;
    }

    public CourierZone getZone() { return zone; }
    public BoardSizeTier getSizeTier() { return sizeTier; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CourierRateCardId that)) return false;
        return zone == that.zone && sizeTier == that.sizeTier;
    }

    @Override
    public int hashCode() {
        return Objects.hash(zone, sizeTier);
    }
}
