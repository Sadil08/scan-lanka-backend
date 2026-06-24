package com.scanlanka.auth;

import com.scanlanka.AbstractIntegrationTest;
import com.scanlanka.auth.domain.AppUser;
import com.scanlanka.auth.domain.Role;
import com.scanlanka.auth.infra.AppUserRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Non-admin → 403 on /api/admin/** (07 AdminGateIT, AC-AUTH-5). */
@TestPropertySource(properties = "app.auth.admin-totp-required=true")
class AdminGateIT extends AbstractIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired AppUserRepository users;
    @Autowired PasswordEncoder encoder;

    @Test
    void customerCannotAccessAdminRoutes() throws Exception {
        String email = "cust-admin-gate@scanlanka.lk";
        Cookie customer = AuthTestSupport.loginVerifiedCustomer(mvc, users, email);

        mvc.perform(get("/api/admin/orders").cookie(customer))
            .andExpect(status().isForbidden());
    }

    @Test
    void unauthenticatedCannotAccessAdminRoutes() throws Exception {
        mvc.perform(get("/api/admin/orders")).andExpect(status().isForbidden());
    }

    @Test
    void adminWithoutTotpCannotAccessAdminRoutes() throws Exception {
        String email = "admin-no-totp@scanlanka.lk";
        AppUser admin = new AppUser(email, encoder.encode("password123"), "Admin", Role.ADMIN);
        admin.setEmailVerified(true);
        users.save(admin);

        MvcResult login = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"password123\"}"))
            .andExpect(status().isOk()).andReturn();
        Cookie access = login.getResponse().getCookie("sl_at");

        mvc.perform(get("/api/admin/orders").cookie(access))
            .andExpect(status().isForbidden());
    }
}
