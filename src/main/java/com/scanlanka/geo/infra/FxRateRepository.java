package com.scanlanka.geo.infra;

import com.scanlanka.geo.domain.FxRate;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FxRateRepository extends JpaRepository<FxRate, String> {}
