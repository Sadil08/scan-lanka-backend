package com.scanlanka.checkout.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Global delivery settings (singleton). {@code lorryMinBillCents} is the retired global min-bill
 * (owner 2026-07-03: replaced by per-cell gates).
 *
 * <p>{@code lorryCapColomboCents}/{@code lorryCapSuburbCents} (owner 2026-07-07) are an
 * <b>unconditional</b> ceiling on the resolved zone's WHOLE lorry total (sum of every priced cell ×
 * qty), on every order regardless of size: Colombo lorry delivery never exceeds Rs 1,000, Suburb never
 * exceeds Rs 1,500 (never bumped UP if the raw sum is already lower). A same-day earlier cut only
 * applied this above a Rs 6,000 trigger; the owner confirmed it should always apply after seeing a
 * Rs 1,687 order charge Rs 1,600 uncapped — the trigger column was dropped (V39). Outer has no cap
 * (its own {@code gateMetOuterCents} flat charge is unchanged, no ceiling role). Admin-editable (08).
 */
@Entity
@Table(name = "delivery_settings")
public class DeliverySettings {

    @Id
    private Short id;

    @Column(name = "lorry_min_bill_cents", nullable = false)
    private long lorryMinBillCents;

    @Column(name = "lorry_cap_colombo_cents", nullable = false)
    private long lorryCapColomboCents;

    @Column(name = "lorry_cap_suburb_cents", nullable = false)
    private long lorryCapSuburbCents;

    @Column(name = "gate_met_outer_cents", nullable = false)
    private long gateMetOuterCents;

    protected DeliverySettings() {}

    public Short getId() { return id; }
    public long getLorryMinBillCents() { return lorryMinBillCents; }
    public void setLorryMinBillCents(long lorryMinBillCents) { this.lorryMinBillCents = lorryMinBillCents; }
    public long getLorryCapColomboCents() { return lorryCapColomboCents; }
    public void setLorryCapColomboCents(long c) { this.lorryCapColomboCents = c; }
    public long getLorryCapSuburbCents() { return lorryCapSuburbCents; }
    public void setLorryCapSuburbCents(long c) { this.lorryCapSuburbCents = c; }
    public long getGateMetOuterCents() { return gateMetOuterCents; }
    public void setGateMetOuterCents(long c) { this.gateMetOuterCents = c; }
}
