package com.scanlanka.geo.app;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Optional;

/** ip-api.com lookup when API key configured (13 T-2). */
@Component
@ConditionalOnProperty(name = "app.geo.provider", havingValue = "ip-api")
public class IpApiGeoClient implements GeoIpClient {

    private final RestClient http;
    private final ObjectMapper json;

    public IpApiGeoClient(@Value("${app.geo.ip-api-base:https://ip-api.com}") String base, ObjectMapper json) {
        this.http = RestClient.builder().baseUrl(base).build();
        this.json = json;
    }

    @Override
    public Optional<String> countryCodeForIp(String ip) {
        if (ip == null || ip.isBlank() || ip.startsWith("127.") || ip.startsWith("::1")) {
            return Optional.empty();
        }
        try {
            String body = http.get().uri("/json/{ip}?fields=status,countryCode", ip).retrieve().body(String.class);
            JsonNode node = json.readTree(body);
            if (node != null && "success".equals(node.path("status").asText())) {
                String code = node.path("countryCode").asText(null);
                return code == null || code.isBlank() ? Optional.empty() : Optional.of(code.toUpperCase());
            }
        } catch (Exception ignored) {
            // degrade gracefully
        }
        return Optional.empty();
    }
}
