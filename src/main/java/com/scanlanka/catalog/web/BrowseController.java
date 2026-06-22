package com.scanlanka.catalog.web;

import com.scanlanka.catalog.app.BrowseFilters;
import com.scanlanka.catalog.app.ProductQueryService;
import com.scanlanka.catalog.web.dto.ProductResponses.ProductChipDTO;
import com.scanlanka.catalog.web.dto.ProductResponses.ProductDetailDTO;
import com.scanlanka.catalog.web.dto.ProductResponses.ResolveVariantRequest;
import com.scanlanka.catalog.web.dto.ProductResponses.ResolveVariantResponse;
import com.scanlanka.shared.ratelimit.RateLimiter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.TimeUnit;

/** Public storefront read API (02-storefront-browse §3). No auth; only visible products. */
@RestController
@RequestMapping("/api/products")
public class BrowseController {

    private static final int MAX_PAGE_SIZE = 60;
    private static final int BROWSE_RATE_LIMIT = 120;
    private static final int BROWSE_RATE_WINDOW_SEC = 60;

    private final ProductQueryService query;
    private final RateLimiter rateLimiter;

    public BrowseController(ProductQueryService query, RateLimiter rateLimiter) {
        this.query = query;
        this.rateLimiter = rateLimiter;
    }

    @GetMapping
    public ResponseEntity<Page<ProductChipDTO>> list(
        @RequestParam(required = false) String q,
        @RequestParam(required = false) Long parentId,
        @RequestParam(required = false) String category,
        @RequestParam(required = false, defaultValue = "newest") String sort,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "24") int size,
        HttpServletRequest http) {
        rateLimiter.check("browse:" + clientIp(http), BROWSE_RATE_LIMIT, BROWSE_RATE_WINDOW_SEC);
        BrowseFilters filters = new BrowseFilters(q, parentId, category, sort);
        Page<ProductChipDTO> body = query.list(filters,
            PageRequest.of(Math.max(0, page), Math.min(size, MAX_PAGE_SIZE)));
        return ResponseEntity.ok()
            .cacheControl(CacheControl.maxAge(60, TimeUnit.SECONDS).cachePublic())
            .body(body);
    }

    @GetMapping("/{slug}")
    public ResponseEntity<ProductDetailDTO> detail(
        @PathVariable String slug,
        @RequestHeader(value = "If-None-Match", required = false) String ifNoneMatch,
        HttpServletRequest http) {
        rateLimiter.check("browse:" + clientIp(http), BROWSE_RATE_LIMIT, BROWSE_RATE_WINDOW_SEC);
        ProductQueryService.DetailView view = query.detail(slug);
        if (ifNoneMatch != null && ifNoneMatch.equals(view.etag())) {
            return ResponseEntity.status(304)
                .cacheControl(CacheControl.maxAge(60, TimeUnit.SECONDS).cachePublic())
                .eTag(view.etag())
                .build();
        }
        return ResponseEntity.ok()
            .cacheControl(CacheControl.maxAge(60, TimeUnit.SECONDS).cachePublic())
            .eTag(view.etag())
            .body(view.dto());
    }

    @PostMapping("/{id}/resolve-variant")
    public ResolveVariantResponse resolveVariant(@PathVariable Long id,
                                                 @RequestBody ResolveVariantRequest req,
                                                 HttpServletRequest http) {
        rateLimiter.check("browse:" + clientIp(http), BROWSE_RATE_LIMIT, BROWSE_RATE_WINDOW_SEC);
        return query.resolveVariant(id, req.selectedOptionIds());
    }

    private static String clientIp(HttpServletRequest request) {
        String fwd = request.getHeader("X-Forwarded-For");
        return (fwd != null && !fwd.isBlank()) ? fwd.split(",")[0].trim() : request.getRemoteAddr();
    }
}
