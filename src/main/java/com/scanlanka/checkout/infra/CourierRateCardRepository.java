package com.scanlanka.checkout.infra;

import com.scanlanka.checkout.domain.CourierRateCard;
import com.scanlanka.checkout.domain.CourierZone;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CourierRateCardRepository extends JpaRepository<CourierRateCard, CourierZone> {
}
