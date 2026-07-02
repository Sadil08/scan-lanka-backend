package com.scanlanka.checkout.infra;

import com.scanlanka.checkout.domain.CourierArea;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CourierAreaRepository extends JpaRepository<CourierArea, String> {
}
