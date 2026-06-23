package com.scanlanka.merch.infra;

import com.scanlanka.merch.domain.FeaturedProduct;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FeaturedProductRepository extends JpaRepository<FeaturedProduct, Long> {
    List<FeaturedProduct> findAllByOrderByDisplayOrderAsc();
}
