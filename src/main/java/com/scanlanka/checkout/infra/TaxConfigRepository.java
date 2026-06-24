package com.scanlanka.checkout.infra;

import com.scanlanka.checkout.domain.TaxConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TaxConfigRepository extends JpaRepository<TaxConfig, Integer> {
    /** Singleton config row — resolved by lowest id, not a hard-coded id=1 (P2-1). */
    Optional<TaxConfig> findFirstByOrderByIdAsc();
}
