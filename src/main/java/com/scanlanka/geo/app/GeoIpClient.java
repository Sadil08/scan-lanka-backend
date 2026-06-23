package com.scanlanka.geo.app;

import java.util.Optional;

/** Hosted geo-IP lookup (13 FR-GEO-1). */
public interface GeoIpClient {
    Optional<String> countryCodeForIp(String ip);
}
