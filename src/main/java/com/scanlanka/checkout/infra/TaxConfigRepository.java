package com.scanlanka.checkout.infra;

import com.scanlanka.checkout.domain.TaxConfig;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TaxConfigRepository extends JpaRepository<TaxConfig, Integer> {
}
