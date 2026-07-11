package com.scanlanka.order.infra;

import com.scanlanka.order.domain.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {
    List<OrderItem> findByOrderId(Long orderId);
    boolean existsByProductId(Long productId);   // for catalog soft-archive-if-ordered (FR-CATALOG-10)
    boolean existsByVariantId(Long variantId);    // guard: don't hard-delete a size that has been sold
}
