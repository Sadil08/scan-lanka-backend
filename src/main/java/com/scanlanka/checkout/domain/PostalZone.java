package com.scanlanka.checkout.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Maps a postal code to its lorry zone and courier zone (one row per code, seeded from LK.txt). */
@Entity
@Table(name = "postal_zone")
public class PostalZone {

    @Id
    @Column(name = "postal_code")
    private String postalCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "lorry_zone", nullable = false)
    private LorryZone lorryZone;

    @Enumerated(EnumType.STRING)
    @Column(name = "courier_zone", nullable = false)
    private CourierZone courierZone;

    private String district;
    private String province;

    protected PostalZone() {}

    public PostalZone(String postalCode, LorryZone lorryZone, CourierZone courierZone,
                      String district, String province) {
        this.postalCode = postalCode;
        this.lorryZone = lorryZone;
        this.courierZone = courierZone;
        this.district = district;
        this.province = province;
    }

    public String getPostalCode() { return postalCode; }
    public LorryZone getLorryZone() { return lorryZone; }
    public CourierZone getCourierZone() { return courierZone; }
    public String getDistrict() { return district; }
    public String getProvince() { return province; }
}
