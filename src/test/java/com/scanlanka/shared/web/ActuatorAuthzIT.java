package com.scanlanka.shared.web;

import com.scanlanka.AbstractIntegrationTest;
import com.scanlanka.auth.AuthTestSupport;
import com.scanlanka.auth.domain.AppUser;
import com.scanlanka.auth.infra.AppUserRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Prometheus metrics ADMIN-gated; health public (global/04 observability). */
class ActuatorAuthzIT extends AbstractIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired AppUserRepository users;
    @Autowired PasswordEncoder encoder;

    private Cookie adminCookie;

    @BeforeEach
    void seedAdmin() throws Exception {
        String email = "metrics-" + System.nanoTime() + "@scanlanka.lk";
        AppUser admin = AuthTestSupport.seedAdmin(users, encoder, email);
        adminCookie = AuthTestSupport.loginAdmin(mvc, admin.getEmail(), admin.getTotpSecret());
    }

    @Test
    void healthIsPublicPrometheusRequiresAdmin() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
        mvc.perform(get("/actuator/prometheus")).andExpect(status().isForbidden());
        mvc.perform(get("/actuator/prometheus").cookie(adminCookie)).andExpect(status().isOk());
    }

    @Test
    void cspReportIsPublicAndAcceptsPost() throws Exception {
        mvc.perform(post("/api/csp-report").contentType("application/csp-report")
                .content("{\"csp-report\":{\"violated-directive\":\"script-src\"}}"))
            .andExpect(status().isNoContent());
    }
}
