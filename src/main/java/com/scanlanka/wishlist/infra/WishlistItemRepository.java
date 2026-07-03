package com.scanlanka.wishlist.infra;

import com.scanlanka.wishlist.domain.WishlistItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface WishlistItemRepository extends JpaRepository<WishlistItem, Long> {
    List<WishlistItem> findByCustomerIdOrderByCreatedAtDesc(Long customerId);
    boolean existsByCustomerIdAndProductId(Long customerId, Long productId);
    Optional<WishlistItem> findByCustomerIdAndProductId(Long customerId, Long productId);
    void deleteByCustomerIdAndProductId(Long customerId, Long productId);

    @Query("SELECT DISTINCT w.customerId FROM WishlistItem w WHERE w.productId = :productId")
    List<Long> findCustomerIdsByProductId(Long productId);
}
