package com.scanlanka.catalog.app;

import com.scanlanka.catalog.domain.Product;
import com.scanlanka.catalog.domain.ProductImage;
import com.scanlanka.catalog.infra.ProductImageRepository;
import com.scanlanka.catalog.infra.ProductRepository;
import com.scanlanka.shared.storage.ImageStorage;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/** Admin image upload (01 FR-CATALOG-2). Validates + re-encodes, stores backend-side, one preview/product. */
@Service
public class ImageService {

    private final ProductRepository products;
    private final ProductImageRepository images;
    private final ImageProcessing processing;
    private final ImageStorage storage;
    private final CatalogCacheEvictor cacheEvictor;

    public ImageService(ProductRepository products, ProductImageRepository images,
                        ImageProcessing processing, ImageStorage storage,
                        CatalogCacheEvictor cacheEvictor) {
        this.products = products;
        this.images = images;
        this.processing = processing;
        this.storage = storage;
        this.cacheEvictor = cacheEvictor;
    }

    public record StoredImageView(long id, String url, boolean preview) {}

    @Transactional(readOnly = true)
    public List<StoredImageView> list(Long productId) {
        ensureProduct(productId);
        return images.findByProductIdOrderByDisplayOrderAsc(productId).stream()
            .map(i -> new StoredImageView(i.getId(), i.getUrl(), i.isPreview()))
            .toList();
    }

    @Transactional
    public StoredImageView upload(Long productId, byte[] fileBytes, boolean isPreview) {
        Product product = ensureProduct(productId);

        byte[] png = processing.validateAndReencode(fileBytes);              // T-6/T-21 hardening
        ImageStorage.StoredImage stored = storage.store(png, processing.outputExtension());

        if (isPreview) {                                                     // enforce single preview
            images.findFirstByProductIdAndPreviewTrue(product.getId()).ifPresent(prev -> {
                prev.setPreview(false);
                images.saveAndFlush(prev);
            });
        }
        int order = images.findByProductIdOrderByDisplayOrderAsc(product.getId()).size();
        ProductImage saved = images.save(
            new ProductImage(product.getId(), stored.key(), stored.url(), isPreview, order));
        cacheEvictor.evictAll();
        return new StoredImageView(saved.getId(), stored.url(), saved.isPreview());
    }

    @Transactional
    public void delete(Long productId, Long imageId) {
        ProductImage img = images.findById(imageId)
            .filter(i -> i.getProductId().equals(productId))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Image not found"));
        boolean wasPreview = img.isPreview();
        images.delete(img);
        if (wasPreview) {
            images.findByProductIdOrderByDisplayOrderAsc(productId).stream().findFirst()
                .ifPresent(next -> { next.setPreview(true); images.save(next); });
        }
        cacheEvictor.evictAll();
    }

    @Transactional
    public StoredImageView setPreview(Long productId, Long imageId) {
        ProductImage img = images.findById(imageId)
            .filter(i -> i.getProductId().equals(productId))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Image not found"));
        images.findFirstByProductIdAndPreviewTrue(productId).ifPresent(prev -> {
            if (!prev.getId().equals(imageId)) {
                prev.setPreview(false);
                images.save(prev);
            }
        });
        img.setPreview(true);
        images.save(img);
        cacheEvictor.evictAll();
        return new StoredImageView(img.getId(), img.getUrl(), true);
    }

    private Product ensureProduct(Long productId) {
        return products.findById(productId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found"));
    }
}
