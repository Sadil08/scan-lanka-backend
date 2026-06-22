package com.scanlanka.auth;

import com.scanlanka.auth.infra.AppUserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Admin TOTP enrolment unlocks /api/admin/** (07 FR-AUTH-10). */
class TwoFactorIT extends AbstractIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired AppUserRepository users;
    @Autowired org.springframework.security.crypto.password.PasswordEncoder encoder;

    private static final String SECRET = "JBSWY3DPEHPK3PXP";

    @Test
    void adminEnrolsTotpThenAccessesDashboard() throws Exception {
        String email = "admin-2fa@scanlanka.lk";
        AuthTestSupport.seedAdmin(users, encoder, email);

        var login = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"password123\",\"totp\":\""
                    + AuthTestSupport.currentTotp(SECRET) + "\"}"))
            .andExpect(status().isOk()).andReturn();
        var access = login.getResponse().getCookie("sl_at");

        mvc.perform(get("/api/admin/orders/dashboard").cookie(access))
            .andExpect(status().isOk());
    }

    @Test
    void setupAndEnableFlow() throws Exception {
        String email = "admin-setup@scanlanka.lk";
        var admin = new com.scanlanka.auth.domain.AppUser(
            email, encoder.encode("password123"), "Admin", com.scanlanka.auth.domain.Role.ADMIN);
        admin.setEmailVerified(true);
        users.save(admin);

        var login = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"password123\"}"))
            .andExpect(status().isOk()).andReturn();
        var access = login.getResponse().getCookie("sl_at");

        mvc.perform(get("/api/admin/orders/dashboard").cookie(access))
            .andExpect(status().isForbidden());

        var setup = mvc.perform(post("/api/auth/2fa/setup").cookie(access))
            .andExpect(status().isOk()).andReturn();
        String secret = new com.fasterxml.jackson.databind.ObjectMapper()
            .readTree(setup.getResponse().getContentAsString()).get("secret").asText();

        mvc.perform(post("/api/auth/2fa/enable").cookie(access).contentType(MediaType.APPLICATION_JSON)
                .content("{\"totp\":\"" + AuthTestSupport.currentTotp(secret) + "\"}"))
            .andExpect(status().isOk());

        mvc.perform(get("/api/admin/orders/dashboard").cookie(access))
            .andExpect(status().isOk());
    }
}
