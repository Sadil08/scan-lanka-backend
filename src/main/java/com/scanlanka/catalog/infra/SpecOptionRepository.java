package com.scanlanka.catalog.infra;

import com.scanlanka.catalog.domain.SpecOption;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SpecOptionRepository extends JpaRepository<SpecOption, Long> {
    List<SpecOption> findBySpecGroupIdOrderByDisplayOrderAsc(Long specGroupId);
    Optional<SpecOption> findBySpecGroupIdAndValue(Long specGroupId, String value);
}
