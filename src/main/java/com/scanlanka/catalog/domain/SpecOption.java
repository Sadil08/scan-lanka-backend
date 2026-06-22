package com.scanlanka.catalog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** An option within a spec group (e.g. "3ft×4ft", "6×7", "5mm"). */
@Entity
@Table(name = "spec_option")
public class SpecOption {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "spec_group_id", nullable = false)
    private Long specGroupId;

    @Column(nullable = false)
    private String value;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    protected SpecOption() {}

    public SpecOption(Long specGroupId, String value, int displayOrder) {
        this.specGroupId = specGroupId;
        this.value = value;
        this.displayOrder = displayOrder;
    }

    public Long getId() { return id; }
    public Long getSpecGroupId() { return specGroupId; }
    public String getValue() { return value; }
    public int getDisplayOrder() { return displayOrder; }
}
