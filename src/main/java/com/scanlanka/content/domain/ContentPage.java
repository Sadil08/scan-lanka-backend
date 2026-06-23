package com.scanlanka.content.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

@Entity
@Table(name = "content_page")
public class ContentPage {

    @Id
    @Column(length = 80)
    private String slug;

    @Column(nullable = false)
    private String title;

    @Column(name = "body_html", nullable = false)
    private String bodyHtml;

    @Column(name = "updated_by")
    private Long updatedBy;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ContentPage() {}

    public ContentPage(String slug, String title, String bodyHtml) {
        this.slug = slug;
        this.title = title;
        this.bodyHtml = bodyHtml;
    }

    public String getSlug() { return slug; }
    public String getTitle() { return title; }
    public String getBodyHtml() { return bodyHtml; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void update(String title, String bodyHtml, Long updatedBy) {
        this.title = title;
        this.bodyHtml = bodyHtml;
        this.updatedBy = updatedBy;
    }
}
