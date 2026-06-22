package com.scanlanka.order;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scanlanka.auth.AuthTestSupport;
import com.scanlanka.AbstractIntegrationTest;
import com.scanlanka.auth.infra.AppUserRepository;
import com.scanlanka.catalog.app.ProductService;
import com.scanlanka.catalog.web.dto.ProductRequests.CreateProductRequest;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Customer order isolation (09 OrderAuthzIT). */
class OrderAuthzIT extends AbstractIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ProductService productService;
    @Autowired AppUserRepository users;
    @Autowired ObjectMapper objectMapper;

    private Long seedProduct() {
        return productService.create(new CreateProductRequest(
            null, "Order Auth " + System.nanoTime(), null, null, null, "X", null, 10, 300L,
            List.of(), List.of()));
    }

    @Test
    void customerBCannotReadCustomerAOrder() throws Exception {
        Long productId = seedProduct();
        Cookie a = AuthTestSupport.loginVerifiedCustomer(mvc, users, "ordera@scanlanka.lk");
        Cookie b = AuthTestSupport.loginVerifiedCustomer(mvc, users, "orderb@scanlanka.lk");

        MvcResult placed = mvc.perform(post("/api/checkout").cookie(a).contentType(MediaType.APPLICATION_JSON)
                .content("{\"items\":[{\"productId\":" + productId + ",\"quantity\":1}],"
                    + "\"fulfilmentType\":\"PICKUP_SHOP\",\"deliveryPayment\":\"PREPAID\","
                    + "\"contactName\":\"A\",\"contactPhone\":\"+94770000001\",\"contactEmail\":\"ordera@scanlanka.lk\"}"))
            .andExpect(status().isOk())
            .andReturn();

        JsonNode json = objectMapper.readTree(placed.getResponse().getContentAsString());
        String orderNumber = json.get("orderNumber").asText();

        mvc.perform(get("/api/orders/" + orderNumber).cookie(b))
            .andExpect(status().isNotFound());

        mvc.perform(get("/api/orders/" + orderNumber).cookie(a))
            .andExpect(status().isOk());
    }
}
