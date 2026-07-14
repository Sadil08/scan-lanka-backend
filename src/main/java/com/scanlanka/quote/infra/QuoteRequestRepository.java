package com.scanlanka.quote.infra;

import com.scanlanka.quote.domain.QuoteRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface QuoteRequestRepository extends JpaRepository<QuoteRequest, Long> {
    Optional<QuoteRequest> findByAccessTokenHash(String hash);

    /**
     * Admin list filters (11 FR-QUOTE-9): status and/or a Local ('LK') vs International
     * (non-null, non-'LK') scope. Either filter may be null to mean "no filter".
     */
    @Query("""
        SELECT q FROM QuoteRequest q
        WHERE (:status IS NULL OR q.status = :status)
          AND (:scope IS NULL
               OR (:scope = 'LOCAL' AND q.country = 'LK')
               OR (:scope = 'INTL' AND q.country IS NOT NULL AND q.country <> 'LK'))
        ORDER BY q.createdAt DESC
        """)
    Page<QuoteRequest> search(@Param("status") String status, @Param("scope") String scope, Pageable pageable);
}
