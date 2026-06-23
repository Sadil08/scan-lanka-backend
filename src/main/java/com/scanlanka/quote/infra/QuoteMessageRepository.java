package com.scanlanka.quote.infra;

import com.scanlanka.quote.domain.QuoteMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface QuoteMessageRepository extends JpaRepository<QuoteMessage, Long> {
    List<QuoteMessage> findByQuoteIdOrderByCreatedAtAsc(Long quoteId);
}
