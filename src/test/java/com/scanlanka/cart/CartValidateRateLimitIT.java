package com.scanlanka.cart;

import com.scanlanka.AbstractIntegrationTest;
import com.scanlanka.catalog.app.ProductService;
import com.scanlanka.catalog.web.dto.ProductRequests.CreateProductRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Rate limit on public cart validate (04-cart T-8). */
class CartValidateRateLimitIT extends AbstractIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ProductService productService;

    @Test
    void validateRateLimitsAfterThreshold() throws Exception {
        Long productId = productService.create(new CreateProductRequest(
            null, "Rate " + System.nanoTime(), null, null, null, "X", null, 5, 100L,
            List.of(), List.of()));
        String body = "{\"items\":[{\"productId\":" + productId + ",\"quantity\":1}]}";

        for (int i = 0; i < 60; i++) {
            mvc.perform(post("/api/cart/validate").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
        }
        mvc.perform(post("/api/cart/validate").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isTooManyRequests());
    }
}
