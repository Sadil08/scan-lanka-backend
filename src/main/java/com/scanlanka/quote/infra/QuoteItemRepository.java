package com.scanlanka.quote.infra;

import com.scanlanka.quote.domain.QuoteItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface QuoteItemRepository extends JpaRepository<QuoteItem, Long> {
    List<QuoteItem> findByQuoteId(Long quoteId);
}
