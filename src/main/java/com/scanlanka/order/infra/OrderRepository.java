package com.scanlanka.order.infra;

import com.scanlanka.order.domain.Order;
import com.scanlanka.order.domain.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long>, JpaSpecificationExecutor<Order> {
    Optional<Order> findByOrderNumber(String orderNumber);
    List<Order> findByCustomerIdOrderByCreatedAtDesc(Long customerId);

    // Admin orders board (08 FR-ADMIN-5)
    Page<Order> findAllByOrderByCreatedAtDesc(Pageable pageable);
    Page<Order> findByStatusOrderByCreatedAtDesc(OrderStatus status, Pageable pageable);
    long countByStatus(OrderStatus status);

    /** Pending / failed payment rows that are not abandoned PayHere card attempts. */
    @Query("""
        select count(o) from Order o
        where o.status in :statuses
          and (o.paymentMethod is null or o.paymentMethod <> 'CARD')
        """)
    long countByStatusInExcludingCard(@Param("statuses") List<OrderStatus> statuses);

    /** Pending payment rows that are not abandoned PayHere card attempts (those are hidden from the main board). */
    @Query("""
        select count(o) from Order o
        where o.status = :status
          and (o.paymentMethod is null or o.paymentMethod <> 'CARD')
        """)
    long countByStatusExcludingCard(@Param("status") OrderStatus status);

    long countByStatusAndPaymentMethod(OrderStatus status, String paymentMethod);

    @Query("""
        select o from Order o
        where o.status = com.scanlanka.order.domain.OrderStatus.PENDING_PAYMENT
          and o.paymentMethod = 'CARD'
          and o.createdAt < :cutoff
        """)
    List<Order> findAbandonedCardOrders(@Param("cutoff") Instant cutoff);
}
