package com.scanlanka.catalog.web;

import com.scanlanka.catalog.app.AdminCatalogService;
import com.scanlanka.catalog.web.dto.ProductRequests.RenameCategoryRequest;
import com.scanlanka.catalog.web.dto.ProductResponses.CategoryAdminDTO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** Admin category maintenance — categories are product tags, renamed in bulk (01 §3). */
@RestController
@RequestMapping("/api/admin/categories")
public class AdminCategoryController {

    private final AdminCatalogService adminCatalog;

    public AdminCategoryController(AdminCatalogService adminCatalog) {
        this.adminCatalog = adminCatalog;
    }

    @GetMapping
    public List<CategoryAdminDTO> list() {
        return adminCatalog.listCategories();
    }

    @PutMapping("/rename")
    public Map<String, Object> rename(@Valid @RequestBody RenameCategoryRequest req) {
        int updated = adminCatalog.renameCategory(req.from(), req.to());
        return Map.of("updated", updated);
    }
}
