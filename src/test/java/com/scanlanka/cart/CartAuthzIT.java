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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Cart isolation + IDOR + stock cap, end-to-end (04 CartAuthzIT, AC-CART-2/5). */
class CartAuthzIT extends AbstractIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ProductService productService;
    @Autowired ObjectMapper objectMapper;
    @Autowired AppUserRepository users;

    private Long seedSingleProduct(int stock) {
        return productService.create(new CreateProductRequest(
            null, "Marker " + System.nanoTime(), null, null, null, "Accessories", null, stock, 250L,
            List.of(), List.of()));
    }

    private Cookie loginAs(String email) throws Exception {
        return AuthTestSupport.loginVerifiedCustomer(mvc, users, email);
    }

    @Test
    void cartsAreIsolatedAndItemsOwnershipScoped() throws Exception {
        Long productId = seedSingleProduct(100);
        Cookie a = loginAs("carta@scanlanka.lk");
        Cookie b = loginAs("cartb@scanlanka.lk");

        // A adds an item
        mvc.perform(post("/api/cart/items").cookie(a).contentType(MediaType.APPLICATION_JSON)
                .content("{\"productId\":" + productId + ",\"quantity\":2}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.lines.length()").value(1))
            .andExpect(jsonPath("$.subtotalCents").value(500));

        // B's cart is empty (isolated — RLS + app scope)
        mvc.perform(get("/api/cart").cookie(b))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.lines.length()").value(0));

        // B cannot touch A's cart item → 404 (no enumeration)
        MvcResult aCart = mvc.perform(get("/api/cart").cookie(a)).andReturn();
        long itemId = objectMapper.readTree(aCart.getResponse().getContentAsString())
            .get("lines").get(0).get("itemId").asLong();
        mvc.perform(patch("/api/cart/items/" + itemId).cookie(b).contentType(MediaType.APPLICATION_JSON)
                .content("{\"quantity\":5}"))
            .andExpect(status().isNotFound());
    }

    @Test
    void stockCapAndUnauthenticated() throws Exception {
        Long productId = seedSingleProduct(3);
        Cookie a = loginAs("cartc@scanlanka.lk");

        // over stock → 409
        mvc.perform(post("/api/cart/items").cookie(a).contentType(MediaType.APPLICATION_JSON)
                .content("{\"productId\":" + productId + ",\"quantity\":5}"))
            .andExpect(status().isConflict());

        // unauthenticated customer cart → 401
        mvc.perform(get("/api/cart")).andExpect(status().isUnauthorized());
    }
}
