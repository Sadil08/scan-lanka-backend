package com.scanlanka.content.app;

import com.scanlanka.admin.app.AuditService;
import com.scanlanka.content.domain.ContentPage;
import com.scanlanka.content.infra.ContentPageRepository;
import com.scanlanka.shared.text.HtmlSanitizer;
import com.scanlanka.shared.text.TextSanitizer;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;

@Service
public class ContentService {

    private final ContentPageRepository pages;
    private final HtmlSanitizer html;
    private final AuditService audit;
    private final ContentCacheEvictor cache;

    public ContentService(ContentPageRepository pages, HtmlSanitizer html,
                          AuditService audit, ContentCacheEvictor cache) {
        this.pages = pages;
        this.html = html;
        this.audit = audit;
        this.cache = cache;
    }

    public record ContentView(String slug, String title, String bodyHtml, Instant updatedAt) {}

    @Transactional(readOnly = true)
    @Cacheable(value = "content", key = "#slug")
    public ContentView get(String slug) {
        return pages.findById(slug).map(this::toView)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Not found"));
    }

    @Transactional(readOnly = true)
    public List<ContentView> listAll() {
        return pages.findAll().stream().map(this::toView).toList();
    }

    @Transactional
    public ContentView save(String slug, String title, String bodyHtml, long adminId) {
        String safeTitle = TextSanitizer.plain(title, 200, "TITLE");
        String safeBody = html.sanitize(bodyHtml);
        ContentPage page = pages.findById(slug).orElse(new ContentPage(slug, safeTitle, safeBody));
        String before = page.getBodyHtml();
        page.update(safeTitle, safeBody, adminId);
        pages.save(page);
        audit.log(adminId, "CONTENT_SAVE", "content_page", slug, before, safeBody);
        cache.evict(slug);
        return toView(page);
    }

    private ContentView toView(ContentPage p) {
        return new ContentView(p.getSlug(), p.getTitle(), p.getBodyHtml(), p.getUpdatedAt());
    }
}
