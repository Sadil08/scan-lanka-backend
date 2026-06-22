package com.scanlanka;

import org.junit.jupiter.api.Test;

/**
 * Smoke integration test: boots the full app against a real Postgres (Testcontainers), which proves
 * Flyway V1 applies AND JPA `ddl-auto=validate` matches the schema (entities ↔ migration in sync).
 * Runs under failsafe (`mvn verify`) in CI where Docker is available.
 */
class SmokeIT extends AbstractIntegrationTest {

    @Test
    void contextLoadsAndSchemaValidates() {
        // If the Spring context starts, Flyway migrated and Hibernate validated the schema.
    }
}
