package com.scanlanka.catalog.web;

import com.scanlanka.catalog.app.ProductQueryService;
import com.scanlanka.catalog.web.dto.ProductResponses.CatalogFacetsDTO;
import com.scanlanka.catalog.web.dto.ProductResponses.CategoryCountDTO;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.TimeUnit;
import java.util.List;

/** Filter facets for storefront browse (02-storefront-browse §3). */
@RestController
@RequestMapping("/api/catalog")
public class CatalogController {

    private final ProductQueryService query;

    public CatalogController(ProductQueryService query) {
        this.query = query;
    }

    @GetMapping("/facets")
    public ResponseEntity<CatalogFacetsDTO> facets() {
        return ResponseEntity.ok()
            .cacheControl(CacheControl.maxAge(60, TimeUnit.SECONDS).cachePublic())
            .body(query.facets());
    }

    @GetMapping("/categories")
    public ResponseEntity<List<CategoryCountDTO>> categories() {
        return ResponseEntity.ok()
            .cacheControl(CacheControl.maxAge(60, TimeUnit.SECONDS).cachePublic())
            .body(query.categoryCounts());
    }
}
