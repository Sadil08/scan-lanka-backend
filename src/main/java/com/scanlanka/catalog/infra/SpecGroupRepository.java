package com.scanlanka.catalog.infra;

import com.scanlanka.catalog.domain.SpecGroup;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SpecGroupRepository extends JpaRepository<SpecGroup, Long> {
    List<SpecGroup> findByProductIdOrderByDisplayOrderAsc(Long productId);
}
