package com.scanlanka.catalog.infra;

import com.scanlanka.catalog.domain.ProductImage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProductImageRepository extends JpaRepository<ProductImage, Long> {
    List<ProductImage> findByProductIdOrderByDisplayOrderAsc(Long productId);
    Optional<ProductImage> findFirstByProductIdAndPreviewTrue(Long productId);
}
