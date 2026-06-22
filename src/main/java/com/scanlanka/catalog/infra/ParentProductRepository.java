package com.scanlanka.catalog.infra;

import com.scanlanka.catalog.domain.ParentProduct;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ParentProductRepository extends JpaRepository<ParentProduct, Long> {
    boolean existsBySlug(String slug);
}
