package com.scanlanka.checkout.infra;

import com.scanlanka.checkout.domain.DeliverySettings;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DeliverySettingsRepository extends JpaRepository<DeliverySettings, Short> {
    /** Singleton row — resolved by lowest id (P2-1 pattern). */
    Optional<DeliverySettings> findFirstByOrderByIdAsc();
}
