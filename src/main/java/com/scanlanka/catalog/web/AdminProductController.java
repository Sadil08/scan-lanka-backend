package com.scanlanka.catalog.web;

import com.scanlanka.catalog.app.AdminCatalogService;
import com.scanlanka.catalog.app.BulkProductImageService;
import com.scanlanka.catalog.app.ImageService;
import com.scanlanka.catalog.app.ProductService;
import com.scanlanka.catalog.web.dto.ProductRequests.ActiveRequest;
import com.scanlanka.catalog.web.dto.ProductRequests.AddVariantRequest;
import com.scanlanka.catalog.web.dto.ProductRequests.CreateProductRequest;
import com.scanlanka.catalog.web.dto.ProductRequests.DeliveryUpdateRequest;
import com.scanlanka.catalog.web.dto.ProductRequests.GroupInput;
import com.scanlanka.catalog.web.dto.ProductRequests.UpdateProductRequest;
import com.scanlanka.catalog.web.dto.ProductResponses.AdminProductDetailDTO;
import com.scanlanka.catalog.web.dto.ProductResponses.AdminProductRowDTO;
import com.scanlanka.catalog.web.dto.ProductResponses.LorryPricingRowDTO;
import com.scanlanka.catalog.web.dto.ProductResponses.VariantPreviewResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;

/** Admin product management (01 §3). Under /api/admin/** → ADMIN-gated by SecurityConfig. Thin controller. */
@RestController
@RequestMapping("/api/admin/products")
public class AdminProductController {

    private final ProductService productService;
    private final AdminCatalogService adminCatalog;
    private final ImageService imageService;
    private final BulkProductImageService bulkImages;

    public AdminProductController(ProductService productService, AdminCatalogService adminCatalog,
                                  ImageService imageService, BulkProductImageService bulkImages) {
        this.productService = productService;
        this.adminCatalog = adminCatalog;
        this.imageService = imageService;
        this.bulkImages = bulkImages;
    }

    @GetMapping
    public List<AdminProductRowDTO> list() {
        return adminCatalog.listProducts();
    }

    @GetMapping("/{id}")
    public AdminProductDetailDTO get(@PathVariable Long id) {
        return adminCatalog.getProduct(id);
    }

    /** Lorry-pricing overview (08/17, owner 2026-07-07): one row per size across the whole catalog. */
    @GetMapping("/lorry-pricing")
    public List<LorryPricingRowDTO> lorryPricing() {
        return adminCatalog.listLorryPricing();
    }

    @PostMapping
    public ResponseEntity<Map<String, Long>> create(@Valid @RequestBody CreateProductRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("id", productService.create(req)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Void> update(@PathVariable Long id, @Valid @RequestBody UpdateProductRequest req) {
        productService.update(id, req);
        return ResponseEntity.ok().build();
    }

    @PatchMapping("/{id}/active")
    public ResponseEntity<Void> setActive(@PathVariable Long id, @RequestBody ActiveRequest req) {
        productService.setActive(id, req.active());
        return ResponseEntity.ok().build();
    }

    /** Set/replace one variant's (size's) delivery attributes — board size tier + per-zone lorry charges. */
    @PatchMapping("/{id}/variants/{variantId}/delivery")
    public ResponseEntity<Void> setVariantDelivery(@PathVariable Long id, @PathVariable Long variantId,
                                                   @Valid @RequestBody DeliveryUpdateRequest req) {
        productService.updateVariantDelivery(variantId, req.delivery());
        return ResponseEntity.ok().build();
    }

    /** Add a variant (size) to an existing variant product. */
    @PostMapping("/{id}/variants")
    public ResponseEntity<ProductService.VariantView> addVariant(@PathVariable Long id,
                                                                 @Valid @RequestBody AddVariantRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(productService.addVariant(id, req.optionValues(), req.priceCents(), req.stockQty()));
    }

    /** Delete a variant (size) — refused if it has been ordered or is the last one. */
    @DeleteMapping("/{id}/variants/{variantId}")
    public ResponseEntity<Void> deleteVariant(@PathVariable Long id, @PathVariable Long variantId) {
        productService.deleteVariant(id, variantId);
        return ResponseEntity.noContent().build();
    }

    /** Product-level delivery-only update (single-priced products) — used by the lorry-pricing overview. */
    @PatchMapping("/{id}/delivery")
    public ResponseEntity<Void> setProductDelivery(@PathVariable Long id,
                                                   @Valid @RequestBody DeliveryUpdateRequest req) {
        productService.updateProductDelivery(id, req.delivery());
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, String>> delete(@PathVariable Long id) {
        return ResponseEntity.ok(Map.of("outcome", productService.delete(id)));
    }

    @PostMapping("/variants/preview")
    public VariantPreviewResponse previewVariants(@Valid @RequestBody List<GroupInput> groups) {
        return productService.previewVariants(groups);
    }

    @GetMapping("/{id}/images")
    public List<ImageService.StoredImageView> listImages(@PathVariable Long id) {
        return imageService.list(id);
    }

    @PostMapping(value = "/{id}/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ImageService.StoredImageView> uploadImage(
        @PathVariable Long id,
        @RequestParam("file") MultipartFile file,
        @RequestParam(name = "isPreview", defaultValue = "false") boolean isPreview,
        @RequestParam(name = "variantId", required = false) Long variantId) {
        try {
            return ResponseEntity.status(HttpStatus.CREATED)
                .body(imageService.upload(id, variantId, file.getBytes(), isPreview));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @PatchMapping("/{id}/images/{imageId}/preview")
    public ImageService.StoredImageView setPreview(@PathVariable Long id, @PathVariable Long imageId) {
        return imageService.setPreview(id, imageId);
    }

    public record ImageVariantRequest(Long variantId) {}

    @PatchMapping("/{id}/images/{imageId}/variant")
    public ImageService.StoredImageView setImageVariant(@PathVariable Long id, @PathVariable Long imageId,
                                                         @RequestBody ImageVariantRequest req) {
        return imageService.setVariant(id, imageId, req.variantId());
    }

    @DeleteMapping("/{id}/images/{imageId}")
    public ResponseEntity<Void> deleteImage(@PathVariable Long id, @PathVariable Long imageId) {
        imageService.delete(id, imageId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Bulk image import from one zip (owner 2026-07-07). {@code dryRun=true} (default) maps filenames
     * to products/sizes without writing anything, so the admin reviews the plan first; {@code
     * dryRun=false} actually stores the mappable images. Filename convention in BulkProductImageService.
     */
    @PostMapping(value = "/images/bulk-import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public BulkProductImageService.ImportReport bulkImport(
        @RequestParam("file") MultipartFile file,
        @RequestParam(name = "dryRun", defaultValue = "true") boolean dryRun) {
        try {
            byte[] zip = file.getBytes();
            return dryRun ? bulkImages.preview(zip) : bulkImages.apply(zip);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
