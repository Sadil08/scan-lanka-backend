package com.scanlanka.wishlist;

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

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Wishlist CRUD, merge, and isolation (03-wishlist WishlistIT / WishlistAuthzIT). */
class WishlistIT extends AbstractIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ProductService productService;
    @Autowired AppUserRepository users;

    private Long seedProduct() {
        return productService.create(new CreateProductRequest(
            null, "Wish " + System.nanoTime(), null, null, null, "Boards", null, 5, 900L,
            List.of(), List.of()));
    }

    private Cookie loginAs(String email) throws Exception {
        return AuthTestSupport.loginVerifiedCustomer(mvc, users, email);
    }

    @Test
    void addListDeleteAndMergeDedup() throws Exception {
        Long productId = seedProduct();
        Cookie cookie = loginAs("wisha@scanlanka.lk");

        mvc.perform(post("/api/wishlist/" + productId).cookie(cookie))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.added").value(true));

        mvc.perform(get("/api/wishlist").cookie(cookie))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].id").value(productId.intValue()));

        mvc.perform(post("/api/wishlist/merge").cookie(cookie).contentType(MediaType.APPLICATION_JSON)
                .content("{\"productIds\":[" + productId + "," + productId + "]}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1));

        mvc.perform(delete("/api/wishlist/" + productId).cookie(cookie))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.removed").value(true));

        mvc.perform(get("/api/wishlist").cookie(cookie))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void wishlistsAreIsolated() throws Exception {
        Long productId = seedProduct();
        Cookie a = loginAs("wishb@scanlanka.lk");
        Cookie b = loginAs("wishc@scanlanka.lk");

        mvc.perform(post("/api/wishlist/" + productId).cookie(a)).andExpect(status().isOk());

        mvc.perform(get("/api/wishlist").cookie(b))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(0));

        mvc.perform(delete("/api/wishlist/" + productId).cookie(b))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.removed").value(false));
    }

    @Test
    void unauthenticatedWishlistReturns401() throws Exception {
        mvc.perform(get("/api/wishlist")).andExpect(status().isUnauthorized());
    }
}
