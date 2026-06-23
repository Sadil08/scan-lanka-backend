package com.scanlanka.merch.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Entity
@Table(name = "banner")
public class Banner {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "image_storage_key", nullable = false)
    private String imageStorageKey;
    @Column(name = "image_url", nullable = false)
    private String imageUrl;
    @Column(name = "link_url")
    private String linkUrl;
    @Column(name = "display_order", nullable = false)
    private int displayOrder;
    @Column(name = "starts_at")
    private Instant startsAt;
    @Column(name = "ends_at")
    private Instant endsAt;
    @Column(nullable = false)
    private boolean active = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Banner() {}

    public Banner(String imageStorageKey, String imageUrl) {
        this.imageStorageKey = imageStorageKey;
        this.imageUrl = imageUrl;
    }

    public Long getId() { return id; }
    public String getImageStorageKey() { return imageStorageKey; }
    public String getImageUrl() { return imageUrl; }
    public String getLinkUrl() { return linkUrl; }
    public int getDisplayOrder() { return displayOrder; }
    public Instant getStartsAt() { return startsAt; }
    public Instant getEndsAt() { return endsAt; }
    public boolean isActive() { return active; }

    public void setLinkUrl(String linkUrl) { this.linkUrl = linkUrl; }
    public void setDisplayOrder(int displayOrder) { this.displayOrder = displayOrder; }
    public void setStartsAt(Instant startsAt) { this.startsAt = startsAt; }
    public void setEndsAt(Instant endsAt) { this.endsAt = endsAt; }
    public void setActive(boolean active) { this.active = active; }
    public void setImage(String key, String url) {
        this.imageStorageKey = key;
        this.imageUrl = url;
    }
}
