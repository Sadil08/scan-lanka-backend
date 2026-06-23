package com.scanlanka.geo.app;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/** Daily FX refresh with last-known fallback (13 FR-GEO-3). */
@Service
public class FxRefreshService {

    private static final Logger log = LoggerFactory.getLogger(FxRefreshService.class);

    private final CurrencyService currency;

    public FxRefreshService(CurrencyService currency) {
        this.currency = currency;
    }

    @Scheduled(cron = "0 0 3 * * *", zone = "Asia/Colombo")
    public void refresh() {
        try {
            // v1: seed/stub refresh keeps USD near market; external FX API is a later add.
            BigDecimal usd = new BigDecimal("300.000000");
            if (currency.isSaneRate(usd)) {
                currency.upsertRate("USD", usd);
            }
        } catch (Exception e) {
            log.warn("FX refresh failed — keeping last-known rates", e);
        }
    }
}
