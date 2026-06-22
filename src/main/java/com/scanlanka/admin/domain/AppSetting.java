package com.scanlanka.admin.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

/** Admin-tunable key/value setting (08). Read by checkout/payments/contact/geo — one source of truth. */
@Entity
@Table(name = "app_setting")
public class AppSetting {

    @Id
    private String key;

    @Column(nullable = false, columnDefinition = "text")
    private String value;

    @Column(name = "updated_by")
    private Long updatedBy;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected AppSetting() {}

    public AppSetting(String key, String value, Long updatedBy) {
        this.key = key;
        this.value = value;
        this.updatedBy = updatedBy;
    }

    public String getKey() { return key; }
    public String getValue() { return value; }
    public void update(String value, Long updatedBy) {
        this.value = value;
        this.updatedBy = updatedBy;
    }
}
