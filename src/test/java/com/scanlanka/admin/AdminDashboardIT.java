package com.scanlanka.admin;

import com.scanlanka.AbstractIntegrationTest;
import com.scanlanka.auth.AuthTestSupport;
import com.scanlanka.auth.infra.AppUserRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Main admin dashboard KPIs (08 §3). Distinct from /api/admin/orders/dashboard. */
class AdminDashboardIT extends AbstractIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired AppUserRepository users;
    @Autowired PasswordEncoder encoder;

    @Test
    void adminCanLoadDashboard() throws Exception {
        Cookie admin = adminCookie("dash-admin@scanlanka.lk");
        mvc.perform(get("/api/admin/dashboard").cookie(admin))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.pendingPayment").isNumber());
    }

    private Cookie adminCookie(String email) throws Exception {
        AuthTestSupport.seedAdmin(users, encoder, email);
        return AuthTestSupport.loginAdmin(mvc, email, "JBSWY3DPEHPK3PXP");
    }
}
