package com.scanlanka.catalog.infra;

import com.scanlanka.catalog.domain.SpecOption;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SpecOptionRepository extends JpaRepository<SpecOption, Long> {
    List<SpecOption> findBySpecGroupIdOrderByDisplayOrderAsc(Long specGroupId);
}
