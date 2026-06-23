package com.scanlanka.shared.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Verifies the app DB role is non-superuser and non-BYPASSRLS (global/02 §6).
 * Enabled in prod via {@code app.db-role-check.enabled=true}.
 */
@Component
@ConditionalOnProperty(name = "app.db-role-check.enabled", havingValue = "true")
public class DbRoleStartupCheck implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DbRoleStartupCheck.class);

    private final JdbcTemplate jdbc;
    private final boolean failOnViolation;

    public DbRoleStartupCheck(
            JdbcTemplate jdbc,
            @Value("${app.db-role-check.fail-on-violation:true}") boolean failOnViolation) {
        this.jdbc = jdbc;
        this.failOnViolation = failOnViolation;
    }

    @Override
    public void run(ApplicationArguments args) {
        Boolean superuser = jdbc.queryForObject(
            "SELECT rolsuper FROM pg_roles WHERE rolname = current_user", Boolean.class);
        Boolean bypassRls = jdbc.queryForObject(
            "SELECT rolbypassrls FROM pg_roles WHERE rolname = current_user", Boolean.class);
        if (Boolean.TRUE.equals(superuser) || Boolean.TRUE.equals(bypassRls)) {
            String msg = "DB role must be non-superuser and non-BYPASSRLS (current_user="
                + jdbc.queryForObject("SELECT current_user", String.class) + ")";
            if (failOnViolation) {
                throw new IllegalStateException(msg);
            }
            log.error(msg);
        } else {
            log.info("DB role check passed (non-superuser, no BYPASSRLS)");
        }
    }
}
