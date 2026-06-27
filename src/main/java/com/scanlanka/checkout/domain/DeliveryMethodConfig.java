package com.scanlanka.checkout.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Per-rail global enable toggle (admin, 08). No pickup rows. */
@Entity
@Table(name = "delivery_method_config")
public class DeliveryMethodConfig {

    @Id
    @Enumerated(EnumType.STRING)
    private DeliveryMethod method;

    @Column(nullable = false)
    private boolean enabled = true;

    protected DeliveryMethodConfig() {}

    public DeliveryMethodConfig(DeliveryMethod method, boolean enabled) {
        this.method = method;
        this.enabled = enabled;
    }

    public DeliveryMethod getMethod() { return method; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
}
