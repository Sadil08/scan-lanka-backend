package com.scanlanka.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scanlanka.AbstractIntegrationTest;
import com.scanlanka.auth.AuthTestSupport;
import com.scanlanka.auth.infra.AppUserRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SupportChatIT extends AbstractIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired AppUserRepository users;
    @Autowired PasswordEncoder encoder;
    @Autowired ObjectMapper objectMapper;

    @Test
    void startAdminReplyCloseVisitorBlocked() throws Exception {
        MvcResult start = mvc.perform(post("/api/support/conversations").contentType(MediaType.APPLICATION_JSON)
                .header("X-Captcha-Token", "test-captcha-bypass")
                .content("{\"name\":\"Alex\",\"email\":\"alex@x.lk\",\"message\":\"Need help with an order\","
                    + "\"pageContext\":\"/products\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.accessToken").exists())
            .andExpect(jsonPath("$.conversation.status").value("OPEN"))
            .andReturn();

        JsonNode body = objectMapper.readTree(start.getResponse().getContentAsString());
        String token = body.get("accessToken").asText();
        long id = body.get("conversation").get("id").asLong();

        mvc.perform(get("/api/support/chat/" + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.messages[0].sender").value("VISITOR"));

        mvc.perform(post("/api/support/chat/" + token + "/messages").contentType(MediaType.APPLICATION_JSON)
                .content("{\"body\":\"Follow-up question\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.messages.length()").value(2));

        Cookie admin = adminCookie("support-admin@scanlanka.lk");
        mvc.perform(get("/api/admin/support/conversations").cookie(admin))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].id").value(id));

        mvc.perform(post("/api/admin/support/conversations/" + id + "/messages").cookie(admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"body\":\"Happy to help — what is your order number?\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.messages[2].sender").value("ADMIN"))
            .andExpect(jsonPath("$.messages[2].body").value("Happy to help — what is your order number?"));

        mvc.perform(get("/api/support/chat/" + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.messages.length()").value(3));

        mvc.perform(post("/api/admin/support/conversations/" + id + "/close").cookie(admin))
            .andExpect(status().isOk());

        mvc.perform(post("/api/support/chat/" + token + "/messages").contentType(MediaType.APPLICATION_JSON)
                .content("{\"body\":\"One more thing\"}"))
            .andExpect(status().isConflict());

        mvc.perform(get("/api/admin/support/conversations").cookie(admin).param("status", "CLOSED"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].status").value("CLOSED"));
    }

    @Test
    void nonAdminBlocked() throws Exception {
        mvc.perform(get("/api/admin/support/conversations")).andExpect(status().is4xxClientError());
    }

    private Cookie adminCookie(String email) throws Exception {
        AuthTestSupport.seedAdmin(users, encoder, email);
        return AuthTestSupport.loginAdmin(mvc, email, "JBSWY3DPEHPK3PXP");
    }
}
