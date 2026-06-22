package com.scanlanka.checkout.infra;

import com.scanlanka.checkout.domain.DeliveryZonePostalCode;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

public interface DeliveryZonePostalCodeRepository extends JpaRepository<DeliveryZonePostalCode, String> {
    List<DeliveryZonePostalCode> findByZoneId(Long zoneId);

    @Modifying
    @Transactional
    void deleteByZoneId(Long zoneId);
    boolean existsByPostalCodeAndZoneIdNot(String postalCode, Long zoneId);
    Optional<DeliveryZonePostalCode> findByPostalCode(String postalCode);
}
