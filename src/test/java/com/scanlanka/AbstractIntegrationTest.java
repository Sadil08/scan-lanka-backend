package com.scanlanka;

import com.scanlanka.checkout.domain.CourierZone;
import com.scanlanka.checkout.domain.LorryZone;
import com.scanlanka.checkout.domain.PostalZone;
import com.scanlanka.checkout.infra.DeliverySettingsRepository;
import com.scanlanka.checkout.infra.PostalZoneRepository;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.TimeZone;

/**
 * Base for integration tests (global/05 §3): real Postgres + Redis via Testcontainers, Flyway-migrated
 * schema, MockMvc. Subclasses (named *IT) run under failsafe / CI where Docker is available.
 *
 * <p>Each class gets a <strong>fresh</strong> Postgres + Redis (clean DB and rate-limit state) via the
 * per-class {@code @Container} lifecycle. {@link DirtiesContext} closes the Spring context after every
 * class so a context is never reused against a container that a previous class already stopped — that
 * mismatch otherwise strands cached contexts on a dead container and fails the whole suite (HikariPool
 * connection timeouts / Redis fail-closed 429s).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public abstract class AbstractIntegrationTest {

    static {
        // PG 16 rejects deprecated JVM zones like Asia/Calcutta on Windows; force UTC for IT DB connections.
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    }

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
        new PostgreSQLContainer<>("postgres:16").withDatabaseName("scanlanka");

    @Container
    static final GenericContainer<?> REDIS =
        new GenericContainer<>("redis:7").withExposedPorts(6379);

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        // Keep the suite hermetic: force the logging email provider, never real SMTP. A developer's local
        // .env (loaded by spring-dotenv above application.yml) may set MAIL_ENABLED=true with live Gmail
        // creds — without this pin, ITs would register SmtpEmailProvider and the worker would attempt real
        // sends on drain. @DynamicPropertySource outranks the .env source, so this wins everywhere.
        registry.add("app.notifications.smtp-enabled", () -> "false");
    }

    @Autowired(required = false) private PostalZoneRepository postalZonesBase;
    @Autowired(required = false) private DeliverySettingsRepository deliverySettingsBase;

    /**
     * Shared delivery setup for order-placing ITs: a serviceable Colombo postal code (00100) and a
     * lorry minimum bill of 0, so a simple order qualifies for `COMPANY_LORRY` with delivery 0 (its
     * product carries no lorry charge → "arranged"). Tests that exercise the real Rs 6,000 gate
     * (e.g. CheckoutIT) override the setting in their own @BeforeEach.
     */
    @BeforeEach
    void seedDeliveryBaseline() {
        if (postalZonesBase != null && !postalZonesBase.existsById("00100")) {
            postalZonesBase.save(new PostalZone("00100", LorryZone.COLOMBO, CourierZone.COLOMBO_1_15,
                "Colombo", "Western Province"));
        }
        if (deliverySettingsBase != null) {
            deliverySettingsBase.findFirstByOrderByIdAsc().ifPresent(s -> {
                s.setLorryMinBillCents(0);
                deliverySettingsBase.save(s);
            });
        }
    }
}
