package com.scanlanka.merch;

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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Admin merch CRUD + public home payload (08 FR-ADMIN-7). */
class MerchFlowIT extends AbstractIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ProductService productService;
    @Autowired AppUserRepository users;
    @Autowired PasswordEncoder encoder;
    @Autowired ObjectMapper objectMapper;

    @Test
    void adminManagesFeaturedAndBanners() throws Exception {
        long productId = productService.create(new CreateProductRequest(
            null, "Featured " + System.nanoTime(), null, null, null, "Boards", null, 1, 5000L,
            List.of(), List.of()));
        Cookie admin = adminCookie("merch-admin@scanlanka.lk");

        mvc.perform(put("/api/admin/merch/featured").cookie(admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"items\":[{\"productId\":" + productId + ",\"displayOrder\":1}]}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].productId").value(productId));

        MvcResult banner = mvc.perform(post("/api/admin/merch/banners").cookie(admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"linkUrl\":\"/shop\",\"displayOrder\":1,\"active\":true}"))
            .andExpect(status().isOk())
            .andReturn();
        JsonNode bannerBody = objectMapper.readTree(banner.getResponse().getContentAsString());
        long bannerId = bannerBody.get("id").asLong();

        mvc.perform(get("/api/admin/merch/banners").cookie(admin))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[?(@.id == " + bannerId + ")]").exists());

        mvc.perform(get("/api/home"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.featured").isArray());

        mvc.perform(delete("/api/admin/merch/banners/" + bannerId).cookie(admin))
            .andExpect(status().isOk());
    }

    private Cookie adminCookie(String email) throws Exception {
        AuthTestSupport.seedAdmin(users, encoder, email);
        return AuthTestSupport.loginAdmin(mvc, email, "JBSWY3DPEHPK3PXP");
    }
}
