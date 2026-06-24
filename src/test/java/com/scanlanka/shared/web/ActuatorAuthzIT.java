package com.scanlanka.shared.web;

import com.scanlanka.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Health public; Prometheus scrape endpoint wired and reachable without a session cookie (P0-6).
 * Scrapers can't send cookies, so metrics are protected by network isolation instead — in prod
 * {@code management.server.port} moves actuator to an internal port the load balancer never exposes
 * (see application-prod.yml). This test asserts the scrape endpoint actually serves metrics.
 *
 * <p>Uses a real server ({@code RANDOM_PORT} + {@link TestRestTemplate}) rather than MockMvc: actuator
 * endpoints over MockMvc only resolve as the first dispatch of the first Spring context in a JVM, so in
 * a multi-class suite they 404. A real embedded server serves them reliably.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ActuatorAuthzIT extends AbstractIntegrationTest {

    @Autowired TestRestTemplate rest;

    @Test
    void healthIsPublicAndPrometheusIsWired() {
        // health is public
        assertThat(rest.getForEntity("/actuator/health", String.class).getStatusCode())
            .isEqualTo(HttpStatus.OK);

        // the prometheus scrape endpoint is now wired and reachable without a cookie (so a scraper can
        // collect), and it actually serves OpenMetrics/Prometheus exposition output — not a 404.
        ResponseEntity<String> scrape = rest.getForEntity("/actuator/prometheus", String.class);
        assertThat(scrape.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(scrape.getBody()).contains("jvm_");

        // a different non-public actuator path stays ADMIN-gated (anonymous forbidden) …
        assertThat(rest.getForEntity("/actuator/beans", String.class).getStatusCode())
            .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void cspReportIsPublicAndAcceptsPost() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("application/csp-report"));
        ResponseEntity<Void> res = rest.exchange("/api/csp-report", HttpMethod.POST,
            new HttpEntity<>("{\"csp-report\":{\"violated-directive\":\"script-src\"}}", headers), Void.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }
}
