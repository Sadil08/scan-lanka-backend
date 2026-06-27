package com.scanlanka.checkout.infra;

import com.scanlanka.checkout.domain.DeliveryMethod;
import com.scanlanka.checkout.domain.DeliveryMethodConfig;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeliveryMethodConfigRepository extends JpaRepository<DeliveryMethodConfig, DeliveryMethod> {
}
