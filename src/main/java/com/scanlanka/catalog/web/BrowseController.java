package com.scanlanka.catalog.web;

import com.scanlanka.catalog.app.ProductQueryService;
import com.scanlanka.catalog.web.dto.ProductResponses.ProductChipDTO;
import com.scanlanka.catalog.web.dto.ProductResponses.ProductDetailDTO;
import com.scanlanka.catalog.web.dto.ProductResponses.ResolveVariantRequest;
import com.scanlanka.catalog.web.dto.ProductResponses.ResolveVariantResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Public storefront read API (02-storefront-browse §3). No auth; only visible products. */
@RestController
@RequestMapping("/api/products")
public class BrowseController {

    private static final int MAX_PAGE_SIZE = 60;

    private final ProductQueryService query;

    public BrowseController(ProductQueryService query) {
        this.query = query;
    }

    @GetMapping
    public Page<ProductChipDTO> list(@RequestParam(defaultValue = "0") int page,
                                     @RequestParam(defaultValue = "24") int size) {
        return query.list(PageRequest.of(Math.max(0, page), Math.min(size, MAX_PAGE_SIZE)));
    }

    @GetMapping("/{slug}")
    public ProductDetailDTO detail(@PathVariable String slug) {
        return query.detail(slug);
    }

    @PostMapping("/{id}/resolve-variant")
    public ResolveVariantResponse resolveVariant(@PathVariable Long id,
                                                 @RequestBody ResolveVariantRequest req) {
        return query.resolveVariant(id, req.selectedOptionIds());
    }
}
