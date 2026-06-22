package com.scanlanka.checkout.infra;

import com.scanlanka.checkout.domain.DeliveryZonePostalCode;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeliveryZonePostalCodeRepository extends JpaRepository<DeliveryZonePostalCode, String> {
}
