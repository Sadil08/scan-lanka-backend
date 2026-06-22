package com.scanlanka.checkout.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/** Customer saved address (05 FR-CHECKOUT-8). */
@Entity
@Table(name = "address")
public class Address {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    private String label;
    @Column(nullable = false) private String street;
    @Column(nullable = false) private String city;
    @Column(nullable = false) private String province;
    @Column(name = "postal_code", nullable = false) private String postalCode;
    @Column(nullable = false) private String phone;
    @Column(nullable = false) private String email;
    @Column(name = "is_default", nullable = false) private boolean isDefault;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Address() {}

    public Address(Long customerId, String label, String street, String city, String province,
                   String postalCode, String phone, String email, boolean isDefault) {
        this.customerId = customerId;
        this.label = label;
        this.street = street;
        this.city = city;
        this.province = province;
        this.postalCode = postalCode;
        this.phone = phone;
        this.email = email;
        this.isDefault = isDefault;
    }

    public Long getId() { return id; }
    public Long getCustomerId() { return customerId; }
    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
    public String getStreet() { return street; }
    public void setStreet(String street) { this.street = street; }
    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }
    public String getProvince() { return province; }
    public void setProvince(String province) { this.province = province; }
    public String getPostalCode() { return postalCode; }
    public void setPostalCode(String postalCode) { this.postalCode = postalCode; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public boolean isDefault() { return isDefault; }
    public void setDefault(boolean isDefault) { this.isDefault = isDefault; }
}
