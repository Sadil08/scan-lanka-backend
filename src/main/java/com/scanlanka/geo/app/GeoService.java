package com.scanlanka.geo.app;

import com.scanlanka.admin.app.SettingsService;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

/** IP → country + checkout gating signal (13 FR-GEO-1/4). Display-only — not authorization. */
@Service
public class GeoService {

    private static final String DEFAULT_COUNTRY = "LK";

    private final GeoIpClient geoIp;
    private final SettingsService settings;

    public GeoService(GeoIpClient geoIp, SettingsService settings) {
        this.geoIp = geoIp;
        this.settings = settings;
    }

    public record GeoContext(String country, String currency, boolean isSriLanka,
                             boolean canCheckout, String whatsappNumber) {}

    @Cacheable(cacheNames = "geo", key = "#ip + ':' + (#countryOverride == null ? '' : #countryOverride)")
    public GeoContext resolve(String ip, String countryOverride) {
        String country = (countryOverride != null && !countryOverride.isBlank())
            ? countryOverride.trim().toUpperCase()
            : geoIp.countryCodeForIp(ip).orElse(DEFAULT_COUNTRY);
        boolean sl = "LK".equalsIgnoreCase(country);
        String currency = sl ? "LKR" : settings.get("currency_default_foreign").orElse("USD").toUpperCase();
        String wa = sl ? whatsappLocal() : whatsappIntl();
        return new GeoContext(country, currency, sl, sl, wa);
    }

    private String whatsappLocal() {
        return settings.get("whatsapp_local").orElse("0706307685");
    }

    private String whatsappIntl() {
        return settings.get("whatsapp_intl").orElse("0714307685");
    }
}
