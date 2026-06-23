package com.scanlanka.contact;

import com.scanlanka.AbstractIntegrationTest;
import com.scanlanka.auth.AuthTestSupport;
import com.scanlanka.auth.infra.AppUserRepository;
import com.scanlanka.contact.infra.ContactInquiryRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ContactIT extends AbstractIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ContactInquiryRepository inquiries;
    @Autowired AppUserRepository users;
    @Autowired PasswordEncoder encoder;

    @Test
    void submitAndWhatsAppRouting() throws Exception {
        mvc.perform(post("/api/contact").contentType(MediaType.APPLICATION_JSON)
                .header("X-Captcha-Token", "test-captcha-bypass")
                .content("{\"name\":\"Sam\",\"email\":\"sam@x.lk\",\"phone\":\"+9477\","
                    + "\"message\":\"Hello there\"}"))
            .andExpect(status().isOk());

        assertThat(inquiries.findAll()).hasSize(1);

        mvc.perform(get("/api/contact/whatsapp").param("country", "LK"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.number").value("0706307685"));

        mvc.perform(get("/api/contact/whatsapp").param("country", "US"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.number").value("0714307685"));

        Cookie admin = adminCookie("contact-admin@scanlanka.lk");
        mvc.perform(get("/api/admin/inquiries").cookie(admin))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].status").value("NEW"));
    }

    private Cookie adminCookie(String email) throws Exception {
        AuthTestSupport.seedAdmin(users, encoder, email);
        return AuthTestSupport.loginAdmin(mvc, email, "JBSWY3DPEHPK3PXP");
    }
}
