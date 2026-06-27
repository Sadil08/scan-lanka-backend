package com.scanlanka.order;

import com.scanlanka.AbstractIntegrationTest;
import com.scanlanka.catalog.app.ProductService;
import com.scanlanka.catalog.web.dto.ProductRequests.CreateProductRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Order line snapshots survive product delete (09 OrderSnapshotIT). */
class OrderSnapshotIT extends AbstractIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ProductService productService;

    @Test
    void deletedProductStillRendersFromSnapshot() throws Exception {
        Long productId = productService.create(new CreateProductRequest(
            null, "Snapshot Widget", "SKU-SNAP", null, null, "X", null, 5, 400L,
            List.of(), List.of()));

        MvcResult placed = mvc.perform(post("/api/checkout").contentType(MediaType.APPLICATION_JSON)
                .content("{\"items\":[{\"productId\":" + productId + ",\"quantity\":1}],"
                    + "\"deliveryMethod\":\"COMPANY_LORRY\",\"ship\":{\"street\":\"1 Main\",\"city\":\"Colombo\",\"province\":\"Western\",\"postalCode\":\"00100\"},"
                    + "\"contactName\":\"Sam\",\"contactPhone\":\"+94770000000\",\"contactEmail\":\"snap@x.lk\"}"))
            .andExpect(status().isOk())
            .andReturn();

        String body = placed.getResponse().getContentAsString();
        String orderNumber = body.replaceAll(".*\"orderNumber\"\\s*:\\s*\"([^\"]+)\".*", "$1");

        productService.delete(productId);

        mvc.perform(post("/api/orders/lookup/detail").contentType(MediaType.APPLICATION_JSON)
                .content("{\"orderNumber\":\"" + orderNumber + "\",\"email\":\"snap@x.lk\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.lines[0].name").value("Snapshot Widget"))
            .andExpect(jsonPath("$.lines[0].unitPriceCents").value(400));
    }
}
