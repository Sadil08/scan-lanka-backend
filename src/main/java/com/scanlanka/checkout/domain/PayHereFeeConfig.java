package com.scanlanka.checkout.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Admin-tunable PayHere online-card surcharge (singleton row id=1), mirroring {@link TaxConfig}.
 * Charged only on prepaid CARD (PayHere) checkouts, on top of the full door total (subtotal +
 * delivery + tax) — never on bank transfer or COD orders, which never touch the PayHere gateway.
 */
@Entity
@Table(name = "payhere_fee_config")
public class PayHereFeeConfig {

    @Id
    private Integer id;

    @Column(name = "rate_bps", nullable = false)
    private int rateBps;
    @Column(nullable = false)
    private String label;

    protected PayHereFeeConfig() {}

    public int getRateBps() { return rateBps; }
    public String getLabel() { return label; }

    public void update(int rateBps, String label) {
        this.rateBps = rateBps;
        this.label = label != null && !label.isBlank() ? label : "PayHere fee";
    }
}
