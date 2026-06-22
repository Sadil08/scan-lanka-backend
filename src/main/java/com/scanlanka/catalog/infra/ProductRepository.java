package com.scanlanka.catalog.infra;

import com.scanlanka.catalog.domain.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {
    Optional<Product> findBySlug(String slug);
    boolean existsBySku(String sku);
    boolean existsBySlug(String slug);

    // Storefront sees only visible products (02-storefront-browse).
    Page<Product> findByActiveTrueAndArchivedFalse(Pageable pageable);
    Optional<Product> findBySlugAndActiveTrueAndArchivedFalse(String slug);
}
