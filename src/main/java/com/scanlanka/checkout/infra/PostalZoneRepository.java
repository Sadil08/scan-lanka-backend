package com.scanlanka.checkout.infra;

import com.scanlanka.checkout.domain.PostalZone;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PostalZoneRepository extends JpaRepository<PostalZone, String> {

    /**
     * Admin search over the ~1,800 seeded codes (08): postal code prefix, or district/province
     * substring, so "Gampaha" finds every code in the district and "116" finds 11600.
     */
    @Query("""
        SELECT z FROM PostalZone z
        WHERE LOWER(z.postalCode) LIKE LOWER(CONCAT(:q, '%'))
           OR LOWER(COALESCE(z.district, '')) LIKE LOWER(CONCAT('%', :q, '%'))
           OR LOWER(COALESCE(z.province, '')) LIKE LOWER(CONCAT('%', :q, '%'))
        """)
    Page<PostalZone> search(@Param("q") String q, Pageable pageable);
}
