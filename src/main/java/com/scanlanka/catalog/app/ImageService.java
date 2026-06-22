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

/** Admin image upload (01 FR-CATALOG-2). Validates + re-encodes, stores backend-side, one preview/product. */
@Service
public class ImageService {

    private final ProductRepository products;
    private final ProductImageRepository images;
    private final ImageProcessing processing;
    private final ImageStorage storage;

    public ImageService(ProductRepository products, ProductImageRepository images,
                        ImageProcessing processing, ImageStorage storage) {
        this.products = products;
        this.images = images;
        this.processing = processing;
        this.storage = storage;
    }

    public record StoredImageView(long id, String url, boolean preview) {}

    @Transactional
    public StoredImageView upload(Long productId, byte[] fileBytes, boolean isPreview) {
        Product product = products.findById(productId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found"));

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
        return new StoredImageView(saved.getId(), stored.url(), saved.isPreview());
    }
}
