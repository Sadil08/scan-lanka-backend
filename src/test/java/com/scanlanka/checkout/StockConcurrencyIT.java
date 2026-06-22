package com.scanlanka.checkout;

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

/** Stock soft-reserve prevents oversell at checkout (05 StockConcurrencyIT). */
class StockConcurrencyIT extends AbstractIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ProductService productService;

    @Test
    void secondCheckoutFailsAfterLastUnitReserved() throws Exception {
        Long productId = productService.create(new CreateProductRequest(
            null, "Last One " + System.nanoTime(), null, null, null, "X", null, 1, 500L,
            List.of(), List.of()));

        String body = "{\"items\":[{\"productId\":" + productId + ",\"quantity\":1}],"
            + "\"fulfilmentType\":\"PICKUP_SHOP\",\"deliveryPayment\":\"PREPAID\","
            + "\"contactName\":\"X\",\"contactPhone\":\"+94770000000\",\"contactEmail\":\"x@y.lk\"}";

        mvc.perform(post("/api/checkout").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isOk());

        mvc.perform(post("/api/checkout").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isConflict());
    }
}
