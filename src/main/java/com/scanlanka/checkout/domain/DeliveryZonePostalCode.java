package com.scanlanka.checkout.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Maps a serviceable postal code to its zone (one zone per code). */
@Entity
@Table(name = "delivery_zone_postal_code")
public class DeliveryZonePostalCode {

    @Id
    @Column(name = "postal_code")
    private String postalCode;

    @Column(name = "zone_id", nullable = false)
    private Long zoneId;

    protected DeliveryZonePostalCode() {}

    public String getPostalCode() { return postalCode; }
    public Long getZoneId() { return zoneId; }
}
