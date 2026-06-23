package com.scanlanka.quote;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scanlanka.AbstractIntegrationTest;
import com.scanlanka.auth.AuthTestSupport;
import com.scanlanka.auth.infra.AppUserRepository;
import com.scanlanka.catalog.app.ProductService;
import com.scanlanka.catalog.web.dto.ProductRequests.CreateProductRequest;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class QuoteIT extends AbstractIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ProductService productService;
    @Autowired AppUserRepository users;
    @Autowired PasswordEncoder encoder;
    @Autowired ObjectMapper objectMapper;

    @Test
    void submitNegotiateAcceptConvert() throws Exception {
        long productId = productService.create(new CreateProductRequest(
            null, "Bulk " + System.nanoTime(), null, null, null, "X", null, 50, 5000L,
            List.of(), List.of()));

        MvcResult submit = mvc.perform(post("/api/quotes").contentType(MediaType.APPLICATION_JSON)
                .header("X-Captcha-Token", "test-captcha-bypass")
                .content("{\"requesterName\":\"Intl Co\",\"email\":\"buyer@abroad.com\","
                    + "\"phone\":\"+1555\",\"country\":\"US\",\"message\":\"Need 10 units\","
                    + "\"items\":[{\"productId\":" + productId + ",\"quantity\":10}]}"))
            .andExpect(status().isOk()).andReturn();
        JsonNode body = objectMapper.readTree(submit.getResponse().getContentAsString());
        String token = body.get("accessToken").asText();
        long id = body.get("id").asLong();

        Cookie admin = adminCookie("quote-admin@scanlanka.lk");
        mvc.perform(post("/api/admin/quotes/" + id + "/messages").cookie(admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"body\":\"We can do LKR 45000\",\"quotedPriceCents\":4500000}"))
            .andExpect(status().isOk());

        mvc.perform(get("/api/quotes/" + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.quotedTotalCents").value(4500000));

        mvc.perform(post("/api/quotes/" + token + "/accept"))
            .andExpect(status().isOk());

        mvc.perform(post("/api/admin/quotes/" + id + "/convert").cookie(admin))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.orderNumber").exists());
    }

    @Test
    void nonAdminBlocked() throws Exception {
        mvc.perform(get("/api/admin/quotes")).andExpect(status().is4xxClientError());
    }

    private Cookie adminCookie(String email) throws Exception {
        AuthTestSupport.seedAdmin(users, encoder, email);
        return AuthTestSupport.loginAdmin(mvc, email, "JBSWY3DPEHPK3PXP");
    }
}
