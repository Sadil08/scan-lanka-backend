package com.scanlanka.catalog.infra;

import com.scanlanka.catalog.domain.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {
    Optional<Product> findBySlug(String slug);
    boolean existsBySku(String sku);
    boolean existsBySlug(String slug);

    // Storefront sees only visible products (02-storefront-browse).
    Page<Product> findByActiveTrueAndArchivedFalse(Pageable pageable);
    Optional<Product> findBySlugAndActiveTrueAndArchivedFalse(String slug);

    /** Atomic conditional decrement for SINGLE products (no oversell, T-10). */
    @Modifying(clearAutomatically = true)
    @Query("update Product p set p.stockQty = p.stockQty - :qty where p.id = :id and p.stockQty >= :qty")
    int decrementIfAvailable(@Param("id") Long id, @Param("qty") int qty);
}
