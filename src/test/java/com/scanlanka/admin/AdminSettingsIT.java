package com.scanlanka.admin;

import com.scanlanka.AbstractIntegrationTest;
import com.scanlanka.auth.AuthTestSupport;
import com.scanlanka.auth.infra.AppUserRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Admin store settings read/update (08 FR-ADMIN-4). */
class AdminSettingsIT extends AbstractIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired AppUserRepository users;
    @Autowired PasswordEncoder encoder;

    @Test
    void adminCanReadAndUpdateSettings() throws Exception {
        Cookie admin = adminCookie("settings-admin@scanlanka.lk");

        mvc.perform(get("/api/admin/settings").cookie(admin))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.codEnabled").isBoolean());

        mvc.perform(put("/api/admin/settings").cookie(admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"whatsappLocal\":\"0700000000\",\"whatsappIntl\":\"0710000000\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.whatsappLocal").value("0700000000"))
            .andExpect(jsonPath("$.whatsappIntl").value("0710000000"));
    }

    private Cookie adminCookie(String email) throws Exception {
        AuthTestSupport.seedAdmin(users, encoder, email);
        return AuthTestSupport.loginAdmin(mvc, email, "JBSWY3DPEHPK3PXP");
    }
}
