package com.scanlanka.geo.app;

import com.scanlanka.admin.app.SettingsService;
import com.scanlanka.geo.domain.FxRate;
import com.scanlanka.geo.infra.FxRateRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.Map;

/** LKR base → indicative display currency (13 FR-GEO-2/5). Charge always stays LKR. */
@Service
public class CurrencyService {

    private static final BigDecimal MIN_RATE = new BigDecimal("50");
    private static final BigDecimal MAX_RATE = new BigDecimal("500");

    private final FxRateRepository rates;
    private final SettingsService settings;

    public CurrencyService(FxRateRepository rates, SettingsService settings) {
        this.rates = rates;
        this.settings = settings;
    }

    public record DisplayPrice(long lkrCents, long displayCents, String currency, boolean indicative) {}

    @Transactional(readOnly = true)
    public DisplayPrice display(long lkrCents, String country) {
        String currency = currencyForCountry(country);
        if ("LKR".equals(currency)) {
            return new DisplayPrice(lkrCents, lkrCents, "LKR", false);
        }
        BigDecimal rate = rateOrLastKnown(currency);
        long foreign = convert(lkrCents, rate);
        return new DisplayPrice(lkrCents, foreign, currency, true);
    }

    @Transactional(readOnly = true)
    public Map<String, BigDecimal> allRates() {
        Map<String, BigDecimal> out = new LinkedHashMap<>();
        rates.findAll().forEach(r -> out.put(r.getCurrency(), r.getRateToLkr()));
        return out;
    }

    public boolean isSaneRate(BigDecimal rate) {
        return rate != null && rate.compareTo(MIN_RATE) >= 0 && rate.compareTo(MAX_RATE) <= 0;
    }

    @Transactional
    public void upsertRate(String currency, BigDecimal rate) {
        if (!isSaneRate(rate)) return;
        rates.findById(currency).ifPresentOrElse(
            r -> { r.setRateToLkr(rate); rates.save(r); },
            () -> rates.save(new FxRate(currency, rate)));
    }

    static long convert(long lkrCents, BigDecimal rateToLkr) {
        if (rateToLkr == null || rateToLkr.signum() <= 0) return lkrCents;
        BigDecimal lkr = BigDecimal.valueOf(lkrCents);
        return lkr.divide(rateToLkr, 0, RoundingMode.HALF_UP).longValue();
    }

    private String currencyForCountry(String country) {
        if (country != null && "LK".equalsIgnoreCase(country)) return "LKR";
        return settings.get("currency_default_foreign").orElse("USD").toUpperCase();
    }

    private BigDecimal rateOrLastKnown(String currency) {
        return rates.findById(currency).map(FxRate::getRateToLkr).orElse(new BigDecimal("300"));
    }
}
