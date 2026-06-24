package com.scanlanka;

import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

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
    }
}
