package com.scanlanka.geo.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "fx_rate")
public class FxRate {

    @Id
    @Column(length = 3)
    private String currency;

    @Column(name = "rate_to_lkr", nullable = false)
    private BigDecimal rateToLkr;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected FxRate() {}

    public FxRate(String currency, BigDecimal rateToLkr) {
        this.currency = currency;
        this.rateToLkr = rateToLkr;
    }

    public String getCurrency() { return currency; }
    public BigDecimal getRateToLkr() { return rateToLkr; }

    public void setRateToLkr(BigDecimal rateToLkr) { this.rateToLkr = rateToLkr; }
}
