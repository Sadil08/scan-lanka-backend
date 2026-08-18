package com.scanlanka.checkout.infra;

import com.scanlanka.checkout.domain.PayHereFeeConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PayHereFeeConfigRepository extends JpaRepository<PayHereFeeConfig, Integer> {
    /** Singleton config row — resolved by lowest id, not a hard-coded id=1 (P2-1, same as TaxConfig). */
    Optional<PayHereFeeConfig> findFirstByOrderByIdAsc();
}
