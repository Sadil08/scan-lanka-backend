package com.scanlanka.shared.web;

import com.scanlanka.AbstractIntegrationTest;
import com.scanlanka.auth.AuthTestSupport;
import com.scanlanka.auth.domain.AppUser;
import com.scanlanka.auth.infra.AppUserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Prometheus metrics ADMIN-gated; health public (global/04 observability).
 *
 * <p>Uses a real server ({@code RANDOM_PORT} + {@link TestRestTemplate}) rather than MockMvc: actuator
 * endpoints over MockMvc only resolve as the first dispatch of the first Spring context in a JVM, so in
 * a multi-class suite they 404. A real embedded server serves them reliably.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ActuatorAuthzIT extends AbstractIntegrationTest {

    @Autowired TestRestTemplate rest;
    @Autowired AppUserRepository users;
    @Autowired PasswordEncoder encoder;

    @Test
    void healthIsPublicMetricsRequireAdmin() {
        // health is public
        assertThat(rest.getForEntity("/actuator/health", String.class).getStatusCode())
            .isEqualTo(HttpStatus.OK);

        // a non-public /actuator/** path is ADMIN-gated: anonymous is forbidden …
        assertThat(rest.getForEntity("/actuator/prometheus", String.class).getStatusCode())
            .isEqualTo(HttpStatus.FORBIDDEN);

        // … an admin passes the authorization gate (not 401/403). NOTE: the prometheus scrape endpoint
        // itself is not currently wired (only health+info are exposed), so an authorized admin gets 404
        // here — that observability gap is tracked separately; this test asserts the AUTHZ boundary.
        HttpHeaders authed = new HttpHeaders();
        authed.add(HttpHeaders.COOKIE, adminAccessCookie());
        HttpStatusCode adminStatus = rest.exchange("/actuator/prometheus", HttpMethod.GET,
            new HttpEntity<>(authed), String.class).getStatusCode();
        assertThat(adminStatus).isNotEqualTo(HttpStatus.FORBIDDEN);
        assertThat(adminStatus).isNotEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void cspReportIsPublicAndAcceptsPost() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("application/csp-report"));
        ResponseEntity<Void> res = rest.exchange("/api/csp-report", HttpMethod.POST,
            new HttpEntity<>("{\"csp-report\":{\"violated-directive\":\"script-src\"}}", headers), Void.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    /** Seed an admin and log in over real HTTP, returning the {@code sl_at=...} access cookie. */
    private String adminAccessCookie() {
        String email = "metrics-" + System.nanoTime() + "@scanlanka.lk";
        AppUser admin = AuthTestSupport.seedAdmin(users, encoder, email);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String body = "{\"email\":\"" + admin.getEmail() + "\",\"password\":\"password123\",\"totp\":\""
            + AuthTestSupport.currentTotp(admin.getTotpSecret()) + "\"}";
        ResponseEntity<String> login = rest.exchange("/api/auth/login", HttpMethod.POST,
            new HttpEntity<>(body, headers), String.class);
        List<String> setCookies = login.getHeaders().get(HttpHeaders.SET_COOKIE);
        if (setCookies != null) {
            for (String c : setCookies) {
                if (c.startsWith("sl_at=")) return c.split(";", 2)[0];
            }
        }
        throw new IllegalStateException("login did not return an access cookie: " + login.getStatusCode());
    }
}
