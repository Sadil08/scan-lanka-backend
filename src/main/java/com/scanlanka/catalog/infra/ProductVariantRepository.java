package com.scanlanka.catalog.infra;

import com.scanlanka.catalog.domain.ProductVariant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProductVariantRepository extends JpaRepository<ProductVariant, Long> {
    List<ProductVariant> findByProductId(Long productId);
    Optional<ProductVariant> findByProductIdAndOptionsSignature(Long productId, String signature);

    /** Atomic conditional decrement (no oversell, T-10); 0 rows = insufficient stock. */
    @Modifying(clearAutomatically = true)
    @Query("update ProductVariant v set v.stockQty = v.stockQty - :qty where v.id = :id and v.stockQty >= :qty")
    int decrementIfAvailable(@Param("id") Long id, @Param("qty") int qty);

    @Modifying(clearAutomatically = true)
    @Query("update ProductVariant v set v.stockQty = v.stockQty + :qty where v.id = :id and v.stockQty is not null")
    int incrementStock(@Param("id") Long id, @Param("qty") int qty);
}
