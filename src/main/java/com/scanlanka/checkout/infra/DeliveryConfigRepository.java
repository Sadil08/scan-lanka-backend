package com.scanlanka.checkout.infra;

import com.scanlanka.checkout.domain.DeliveryConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DeliveryConfigRepository extends JpaRepository<DeliveryConfig, Integer> {
    /** Singleton config row — resolved by lowest id, not a hard-coded id=1 (P2-1). */
    Optional<DeliveryConfig> findFirstByOrderByIdAsc();
}
