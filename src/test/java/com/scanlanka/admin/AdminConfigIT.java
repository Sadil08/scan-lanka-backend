package com.scanlanka.admin;

import com.scanlanka.auth.AuthTestSupport;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scanlanka.AbstractIntegrationTest;
import com.scanlanka.auth.infra.AppUserRepository;
import com.scanlanka.catalog.app.ProductService;
import com.scanlanka.catalog.web.dto.ProductRequests.CreateProductRequest;
import com.scanlanka.order.infra.OrderRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Zone/tax changes affect new quotes only; placed order totals immutable (08 AC-ADMIN-1/3). */
class AdminConfigIT extends AbstractIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ProductService productService;
    @Autowired OrderRepository orders;
    @Autowired AppUserRepository users;
    @Autowired PasswordEncoder encoder;
    @Autowired ObjectMapper objectMapper;

    @Test
    void zoneChangeAffectsNewQuoteNotPlacedOrder() throws Exception {
        Long productId = productService.create(new CreateProductRequest(
            null, "Config " + System.nanoTime(), null, null, null, "X", null, 5, 50000L,
            List.of(), List.of()));

        String checkout = "{\"items\":[{\"productId\":" + productId + ",\"quantity\":1}],"
            + "\"fulfilmentType\":\"DELIVERY\",\"deliveryPayment\":\"PREPAID\",\"postalCode\":\"00100\","
            + "\"ship\":{\"street\":\"1\",\"city\":\"Col\",\"province\":\"WP\",\"postalCode\":\"00100\"},"
            + "\"contactName\":\"A\",\"contactPhone\":\"+9477\",\"contactEmail\":\"a@x.lk\"}";

        MvcResult quote1 = mvc.perform(post("/api/checkout/quote").contentType(MediaType.APPLICATION_JSON).content(checkout))
            .andExpect(status().isOk()).andReturn();
        long totalBefore = objectMapper.readTree(quote1.getResponse().getContentAsString()).get("totalCents").asLong();

        MvcResult placed = mvc.perform(post("/api/checkout").contentType(MediaType.APPLICATION_JSON).content(checkout))
            .andExpect(status().isOk()).andReturn();
        String orderNumber = objectMapper.readTree(placed.getResponse().getContentAsString()).get("orderNumber").asText();
        long placedTotal = orders.findByOrderNumber(orderNumber).orElseThrow().getTotalCents();

        Cookie admin = adminCookie("admin-config@scanlanka.lk");
        mvc.perform(put("/api/admin/delivery-zones/1").cookie(admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Colombo Metro\",\"baseChargeCents\":90000,\"perKgChargeCents\":8000,"
                    + "\"fuelPct\":0,\"active\":true,\"postalCodes\":[\"00100\",\"00400\",\"10100\",\"10250\"]}"))
            .andExpect(status().isOk());

        MvcResult quote2 = mvc.perform(post("/api/checkout/quote").contentType(MediaType.APPLICATION_JSON).content(checkout))
            .andExpect(status().isOk()).andReturn();
        long totalAfter = objectMapper.readTree(quote2.getResponse().getContentAsString()).get("totalCents").asLong();

        assertThat(totalAfter).isGreaterThan(totalBefore);
        assertThat(orders.findByOrderNumber(orderNumber).orElseThrow().getTotalCents()).isEqualTo(placedTotal);
    }

    @Test
    void postalOverlapRejected() throws Exception {
        Cookie admin = adminCookie("admin-overlap@scanlanka.lk");
        mvc.perform(post("/api/admin/delivery-zones").cookie(admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Overlap\",\"baseChargeCents\":1000,\"perKgChargeCents\":0,"
                    + "\"fuelPct\":0,\"active\":true,\"postalCodes\":[\"00100\"]}"))
            .andExpect(status().isConflict());
    }

    @Test
    void adminCanCreateAndDeleteZone() throws Exception {
        Cookie admin = adminCookie("admin-zone-del@scanlanka.lk");
        MvcResult created = mvc.perform(post("/api/admin/delivery-zones").cookie(admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Test Zone\",\"baseChargeCents\":1000,\"perKgChargeCents\":0,"
                    + "\"fuelPct\":0,\"active\":true,\"postalCodes\":[\"77777\"]}"))
            .andExpect(status().isOk()).andReturn();
        long zoneId = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();

        mvc.perform(get("/api/admin/delivery-zones/" + zoneId).cookie(admin))
            .andExpect(status().isOk());

        mvc.perform(delete("/api/admin/delivery-zones/" + zoneId).cookie(admin))
            .andExpect(status().isOk());

        mvc.perform(get("/api/admin/delivery-zones/" + zoneId).cookie(admin))
            .andExpect(status().isNotFound());
    }

    private Cookie adminCookie(String email) throws Exception {
        AuthTestSupport.seedAdmin(users, encoder, email);
        return AuthTestSupport.loginAdmin(mvc, email, "JBSWY3DPEHPK3PXP");
    }
}
