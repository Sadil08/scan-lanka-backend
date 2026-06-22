package com.scanlanka.checkout.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;

/** A delivery zone = a set of postal codes sharing rates (05/08). */
@Entity
@Table(name = "delivery_zone")
public class DeliveryZone {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(name = "base_charge_cents", nullable = false)
    private long baseChargeCents;
    @Column(name = "per_kg_charge_cents", nullable = false)
    private long perKgChargeCents;
    @Column(name = "fuel_pct", nullable = false)
    private BigDecimal fuelPct = BigDecimal.ZERO;
    @Column(nullable = false)
    private boolean active = true;

    protected DeliveryZone() {}

    public Long getId() { return id; }
    public String getName() { return name; }
    public long getBaseChargeCents() { return baseChargeCents; }
    public long getPerKgChargeCents() { return perKgChargeCents; }
    public BigDecimal getFuelPct() { return fuelPct; }
    public boolean isActive() { return active; }
}
