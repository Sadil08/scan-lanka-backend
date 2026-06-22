package com.scanlanka.checkout.infra;

import com.scanlanka.checkout.domain.StockReservation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

public interface StockReservationRepository extends JpaRepository<StockReservation, Long> {

    @Query("""
        SELECT COALESCE(SUM(r.quantity), 0) FROM StockReservation r
        WHERE r.released = false AND r.expiresAt > :now
          AND r.productId = :productId
          AND ((:variantId IS NULL AND r.variantId IS NULL) OR r.variantId = :variantId)
        """)
    int sumActiveQuantity(@Param("productId") Long productId, @Param("variantId") Long variantId,
                          @Param("now") Instant now);

    @Modifying
    @Query("UPDATE StockReservation r SET r.released = true WHERE r.released = false AND r.expiresAt <= :now")
    int releaseExpired(@Param("now") Instant now);

    @Modifying
    @Query("UPDATE StockReservation r SET r.released = true WHERE r.orderId = :orderId AND r.released = false")
    int releaseForOrder(@Param("orderId") Long orderId);
}
