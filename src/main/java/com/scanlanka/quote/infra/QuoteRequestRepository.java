package com.scanlanka.quote.infra;

import com.scanlanka.quote.domain.QuoteRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface QuoteRequestRepository extends JpaRepository<QuoteRequest, Long> {
    Page<QuoteRequest> findByStatusOrderByCreatedAtDesc(String status, Pageable pageable);
    Page<QuoteRequest> findAllByOrderByCreatedAtDesc(Pageable pageable);
    Optional<QuoteRequest> findByAccessTokenHash(String hash);
}
