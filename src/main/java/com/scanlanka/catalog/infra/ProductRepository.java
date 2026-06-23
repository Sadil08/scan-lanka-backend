package com.scanlanka.catalog.infra;

import com.scanlanka.catalog.domain.Product;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {
    Optional<Product> findBySlug(String slug);
    boolean existsBySku(String sku);
    boolean existsBySlug(String slug);

    // Storefront sees only visible products (02-storefront-browse).
    Page<Product> findByActiveTrueAndArchivedFalse(Pageable pageable);
    Optional<Product> findBySlugAndActiveTrueAndArchivedFalse(String slug);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Product p WHERE p.id = :id")
    Optional<Product> findByIdForUpdate(@Param("id") Long id);

    @Query("SELECT DISTINCT p.category FROM Product p WHERE p.active = true AND p.archived = false AND p.category IS NOT NULL ORDER BY p.category")
    List<String> findDistinctVisibleCategories();

    @Query("SELECT DISTINCT p.parentProductId FROM Product p WHERE p.active = true AND p.archived = false AND p.parentProductId IS NOT NULL")
    List<Long> findDistinctVisibleParentIds();

    /** Atomic conditional decrement for SINGLE products (no oversell, T-10). */
    @Modifying(clearAutomatically = true)
    @Query("update Product p set p.stockQty = p.stockQty - :qty where p.id = :id and p.stockQty >= :qty")
    int decrementIfAvailable(@Param("id") Long id, @Param("qty") int qty);

    /** Atomic restock after cancel/refund (16 FR-RETURN-4). */
    @Modifying(clearAutomatically = true)
    @Query("update Product p set p.stockQty = p.stockQty + :qty where p.id = :id and p.stockQty is not null")
    int incrementStock(@Param("id") Long id, @Param("qty") int qty);

    @Query("SELECT p FROM Product p WHERE p.archived = false AND p.stockQty IS NOT NULL AND p.stockQty <= :max ORDER BY p.stockQty ASC")
    List<Product> findLowStock(@Param("max") int max);
}
