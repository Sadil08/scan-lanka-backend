package com.scanlanka.checkout.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Global tax config (singleton row id=1). rate in basis points (800 = 8%). */
@Entity
@Table(name = "tax_config")
public class TaxConfig {

    @Id
    private Integer id;

    @Column(name = "rate_bps", nullable = false)
    private int rateBps;
    @Column(nullable = false)
    private String label;

    protected TaxConfig() {}

    public int getRateBps() { return rateBps; }
    public String getLabel() { return label; }
}
