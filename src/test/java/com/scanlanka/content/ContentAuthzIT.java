package com.scanlanka.content;

import com.scanlanka.AbstractIntegrationTest;
import com.scanlanka.auth.AuthTestSupport;
import com.scanlanka.auth.infra.AppUserRepository;
import com.scanlanka.content.infra.ContentPageRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ContentAuthzIT extends AbstractIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ContentPageRepository pages;
    @Autowired AppUserRepository users;
    @Autowired PasswordEncoder encoder;

    @Test
    void publicReadAndAdminEditSanitized() throws Exception {
        mvc.perform(get("/api/content/privacy"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.slug").value("privacy"));

        mvc.perform(put("/api/admin/content/privacy").contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"Privacy\",\"bodyHtml\":\"<p>ok</p><script>alert(1)</script>\"}"))
            .andExpect(status().is4xxClientError());

        Cookie admin = adminCookie("content-admin@scanlanka.lk");
        mvc.perform(put("/api/admin/content/privacy").cookie(admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"Privacy\",\"bodyHtml\":\"<p>Safe</p><script>alert(1)</script>\"}"))
            .andExpect(status().isOk());

        String body = pages.findById("privacy").orElseThrow().getBodyHtml();
        assertThat(body).doesNotContain("<script>");
        assertThat(body).contains("Safe");

        mvc.perform(get("/api/content/privacy"))
            .andExpect(jsonPath("$.bodyHtml").value(org.hamcrest.Matchers.containsString("Safe")));
    }

    private Cookie adminCookie(String email) throws Exception {
        AuthTestSupport.seedAdmin(users, encoder, email);
        return AuthTestSupport.loginAdmin(mvc, email, "JBSWY3DPEHPK3PXP");
    }
}
