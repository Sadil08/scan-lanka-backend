package com.scanlanka.catalog;

import com.scanlanka.AbstractIntegrationTest;
import com.scanlanka.auth.AuthTestSupport;
import com.scanlanka.auth.domain.AppUser;
import com.scanlanka.auth.infra.AppUserRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Admin can list, fetch, and update products; rename categories (01 §3). */
class AdminProductIT extends AbstractIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired AppUserRepository users;
    @Autowired PasswordEncoder encoder;

    private Cookie adminCookie;

    @BeforeEach
    void loginAdmin() throws Exception {
        String email = "catalog-admin-" + System.nanoTime() + "@scanlanka.lk";
        AppUser admin = AuthTestSupport.seedAdmin(users, encoder, email);
        adminCookie = AuthTestSupport.loginAdmin(mvc, admin.getEmail(), admin.getTotpSecret());
    }

    @Test
    void adminCanListGetAndUpdateProduct() throws Exception {
        String create = """
            {"name":"Edit Me Board","category":"Boards","singlePriceCents":5000,"stockQty":3}
            """;
        String id = mvc.perform(post("/api/admin/products").cookie(adminCookie)
                .contentType(MediaType.APPLICATION_JSON).content(create))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        long productId = Long.parseLong(id.replaceAll("\\D", ""));

        mvc.perform(get("/api/admin/products").cookie(adminCookie))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(greaterThanOrEqualTo(1)));

        mvc.perform(get("/api/admin/products/" + productId).cookie(adminCookie))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("Edit Me Board"))
            .andExpect(jsonPath("$.category").value("Boards"));

        mvc.perform(put("/api/admin/products/" + productId).cookie(adminCookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Updated Board\",\"category\":\"Games\",\"singlePriceCents\":5500}"))
            .andExpect(status().isOk());

        mvc.perform(get("/api/admin/products/" + productId).cookie(adminCookie))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("Updated Board"))
            .andExpect(jsonPath("$.category").value("Games"));
    }

    @Test
    void adminCanRenameCategoryAcrossProducts() throws Exception {
        mvc.perform(post("/api/admin/products").cookie(adminCookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"A\",\"category\":\"OldCat\",\"singlePriceCents\":1000}"))
            .andExpect(status().isCreated());
        mvc.perform(post("/api/admin/products").cookie(adminCookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"B\",\"category\":\"OldCat\",\"singlePriceCents\":2000}"))
            .andExpect(status().isCreated());

        mvc.perform(get("/api/admin/categories").cookie(adminCookie))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[?(@.name=='OldCat')].productCount").value(hasItem(2)));

        mvc.perform(put("/api/admin/categories/rename").cookie(adminCookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"from\":\"OldCat\",\"to\":\"NewCat\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.updated").value(2));

        mvc.perform(get("/api/admin/categories").cookie(adminCookie))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[?(@.name=='NewCat')].productCount").value(hasItem(2)));
    }

    @Test
    void unauthenticatedCannotListProducts() throws Exception {
        mvc.perform(get("/api/admin/products")).andExpect(status().isForbidden());
    }

    @Test
    void adminCanPreviewVariants() throws Exception {
        String groups = """
            [{"name":"Size","priceAffecting":true,"options":["Small","Large"]}]
            """;
        mvc.perform(post("/api/admin/products/variants/preview").cookie(adminCookie)
                .contentType(MediaType.APPLICATION_JSON).content(groups))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.rows.length()").value(2))
            .andExpect(jsonPath("$.rows[0].optionValues[0]").value("Small"));
    }
}
