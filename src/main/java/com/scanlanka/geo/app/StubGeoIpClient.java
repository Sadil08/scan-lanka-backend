package com.scanlanka.geo.app;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;

/** Dev/test geo resolver — map known test IPs to countries. */
@Component
@ConditionalOnProperty(name = "app.geo.provider", havingValue = "stub", matchIfMissing = true)
public class StubGeoIpClient implements GeoIpClient {

    private static final Map<String, String> TEST_IPS = Map.of(
        "203.115.0.1", "LK",
        "8.8.8.8", "US",
        "1.1.1.1", "AU");

    @Override
    public Optional<String> countryCodeForIp(String ip) {
        return Optional.ofNullable(TEST_IPS.get(ip));
    }
}
