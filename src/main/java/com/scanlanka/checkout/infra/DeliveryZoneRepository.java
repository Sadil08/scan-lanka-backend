package com.scanlanka.checkout.infra;

import com.scanlanka.checkout.domain.DeliveryZone;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeliveryZoneRepository extends JpaRepository<DeliveryZone, Long> {
}
