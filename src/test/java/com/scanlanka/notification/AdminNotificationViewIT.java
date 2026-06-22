package com.scanlanka.notification;

import com.scanlanka.auth.AuthTestSupport;
import com.scanlanka.AbstractIntegrationTest;
import com.scanlanka.auth.infra.AppUserRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Admin notification audit is ADMIN-gated (10 FR-NOTIFY-7). */
class AdminNotificationViewIT extends AbstractIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired AppUserRepository users;
    @Autowired PasswordEncoder encoder;

    @Test
    void adminCanListNotifications() throws Exception {
        Cookie admin = adminCookie("admin-notify@scanlanka.lk");
        mvc.perform(get("/api/admin/notifications").cookie(admin)).andExpect(status().isOk());
    }

    @Test
    void nonAdminForbidden() throws Exception {
        mvc.perform(get("/api/admin/notifications")).andExpect(status().is4xxClientError());
    }

    private Cookie adminCookie(String email) throws Exception {
        AuthTestSupport.seedAdmin(users, encoder, email);
        return AuthTestSupport.loginAdmin(mvc, email, "JBSWY3DPEHPK3PXP");
    }
}
