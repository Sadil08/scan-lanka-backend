package com.scanlanka.cart;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.scanlanka.auth.AuthTestSupport;
import com.scanlanka.AbstractIntegrationTest;
import com.scanlanka.catalog.app.ProductService;
import com.scanlanka.catalog.web.dto.ProductRequests.CreateProductRequest;
import com.scanlanka.auth.infra.AppUserRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Cart add/update/remove/merge + validate pricing (04-cart CartFlowIT). */
class CartFlowIT extends AbstractIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ProductService productService;
    @Autowired ObjectMapper objectMapper;
    @Autowired AppUserRepository users;

    private Long seedSingleProduct(int stock, long priceCents) {
        return productService.create(new CreateProductRequest(
            null, "Flow Item " + System.nanoTime(), null, null, null, "Accessories", null, stock, priceCents,
            List.of(), List.of()));
    }

    private Cookie loginAs(String email) throws Exception {
        return AuthTestSupport.loginVerifiedCustomer(mvc, users, email);
    }

    @Test
    void addUpdateRemoveAndSubtotal() throws Exception {
        Long productId = seedSingleProduct(10, 500L);
        Cookie cookie = loginAs("flowa@scanlanka.lk");

        mvc.perform(post("/api/cart/items").cookie(cookie).contentType(MediaType.APPLICATION_JSON)
                .content("{\"productId\":" + productId + ",\"quantity\":2}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.subtotalCents").value(1000));

        MvcResult cart = mvc.perform(get("/api/cart").cookie(cookie)).andReturn();
        long itemId = objectMapper.readTree(cart.getResponse().getContentAsString())
            .get("lines").get(0).get("itemId").asLong();

        mvc.perform(patch("/api/cart/items/" + itemId).cookie(cookie).contentType(MediaType.APPLICATION_JSON)
                .content("{\"quantity\":3}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.subtotalCents").value(1500));

        mvc.perform(delete("/api/cart/items/" + itemId).cookie(cookie))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.lines.length()").value(0));
    }

    @Test
    void validateIgnoresClientPriceAndMergeClampsStock() throws Exception {
        Long productId = seedSingleProduct(4, 300L);
        Cookie cookie = loginAs("flowb@scanlanka.lk");

        mvc.perform(post("/api/cart/validate").contentType(MediaType.APPLICATION_JSON)
                .content("{\"items\":[{\"productId\":" + productId + ",\"quantity\":2}]}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.subtotalCents").value(600));

        mvc.perform(post("/api/cart/merge").cookie(cookie).contentType(MediaType.APPLICATION_JSON)
                .content("{\"items\":[{\"productId\":" + productId + ",\"quantity\":3}]}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.lines[0].quantity").value(3));

        mvc.perform(post("/api/cart/merge").cookie(cookie).contentType(MediaType.APPLICATION_JSON)
                .content("{\"items\":[{\"productId\":" + productId + ",\"quantity\":3}]}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.lines[0].quantity").value(4));
    }
}
