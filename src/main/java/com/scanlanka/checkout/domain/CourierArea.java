package com.scanlanka.checkout.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Named place → Citrek courier band (client area definition, 2026). Matched against checkout city. */
@Entity
@Table(name = "courier_area")
public class CourierArea {

    @Id
    @Column(name = "name_normalized", nullable = false)
    private String nameNormalized;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(name = "courier_zone", nullable = false)
    private CourierZone courierZone;

    protected CourierArea() {}

    public String getNameNormalized() { return nameNormalized; }
    public String getDisplayName() { return displayName; }
    public CourierZone getCourierZone() { return courierZone; }
}
