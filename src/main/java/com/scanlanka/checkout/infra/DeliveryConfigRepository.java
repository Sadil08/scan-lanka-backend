package com.scanlanka.checkout.infra;

import com.scanlanka.checkout.domain.DeliveryConfig;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeliveryConfigRepository extends JpaRepository<DeliveryConfig, Integer> {
}
