package com.scanlanka.returns.infra;

import com.scanlanka.returns.domain.Refund;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface RefundRepository extends JpaRepository<Refund, Long> {
    List<Refund> findByOrderIdOrderByCreatedAtDesc(Long orderId);

    Optional<Refund> findByIdempotencyKey(String idempotencyKey);

    @Query("SELECT COALESCE(SUM(r.amountCents), 0) FROM Refund r WHERE r.orderId = :orderId")
    long sumAmountByOrderId(@Param("orderId") Long orderId);
}
