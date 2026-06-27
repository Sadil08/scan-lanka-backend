package com.scanlanka.admin;

import com.scanlanka.auth.AuthTestSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scanlanka.AbstractIntegrationTest;
import com.scanlanka.auth.infra.AppUserRepository;
import com.scanlanka.catalog.app.ProductService;
import com.scanlanka.catalog.domain.Product;
import com.scanlanka.catalog.infra.ProductRepository;
import com.scanlanka.catalog.web.dto.ProductRequests.CreateProductRequest;
import com.scanlanka.checkout.domain.CourierRateCard;
import com.scanlanka.checkout.domain.CourierZone;
import com.scanlanka.checkout.infra.CourierRateCardRepository;
import com.scanlanka.order.infra.OrderRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Delivery-config changes affect new quotes only; placed order snapshots immutable (08 AC-ADMIN-1/3). */
class AdminConfigIT extends AbstractIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ProductService productService;
    @Autowired ProductRepository products;
    @Autowired CourierRateCardRepository courierRates;
    @Autowired OrderRepository orders;
    @Autowired AppUserRepository users;
    @Autowired PasswordEncoder encoder;
    @Autowired ObjectMapper objectMapper;

    @Test
    void courierRateChangeAffectsNewQuoteNotPlacedOrder() throws Exception {
        Long productId = productService.create(new CreateProductRequest(
            null, "Config " + System.nanoTime(), null, null, null, "X", null, 5, 50000L,
            List.of(), List.of()));
        Product p = products.findById(productId).orElseThrow();
        p.setWeightKg(BigDecimal.valueOf(5));            // couriable
        products.save(p);

        String checkout = "{\"items\":[{\"productId\":" + productId + ",\"quantity\":1}],"
            + "\"deliveryMethod\":\"COURIER\",\"postalCode\":\"00100\","
            + "\"ship\":{\"street\":\"1\",\"city\":\"Col\",\"province\":\"WP\",\"postalCode\":\"00100\"},"
            + "\"contactName\":\"A\",\"contactPhone\":\"+9477\",\"contactEmail\":\"a@x.lk\"}";

        long estimateBefore = quoteCourierEstimate(checkout);

        MvcResult placed = mvc.perform(post("/api/checkout").contentType(MediaType.APPLICATION_JSON).content(checkout))
            .andExpect(status().isOk()).andReturn();
        String orderNumber = objectMapper.readTree(placed.getResponse().getContentAsString()).get("orderNumber").asText();
        long snapshot = orders.findByOrderNumber(orderNumber).orElseThrow().getCourierEstimateCents();
        assertThat(snapshot).isEqualTo(estimateBefore);

        // admin raises the Colombo courier base → only NEW quotes reflect it
        CourierRateCard r = courierRates.findById(CourierZone.COLOMBO_1_15).orElseThrow();
        r.update(r.getBaseCents() + 50000, r.getPerKgCents());
        courierRates.save(r);

        assertThat(quoteCourierEstimate(checkout)).isGreaterThan(estimateBefore);
        assertThat(orders.findByOrderNumber(orderNumber).orElseThrow().getCourierEstimateCents()).isEqualTo(snapshot);
    }

    private long quoteCourierEstimate(String body) throws Exception {
        MvcResult q = mvc.perform(post("/api/checkout/quote").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(q.getResponse().getContentAsString()).get("courierEstimateCents").asLong();
    }

    // Legacy delivery-zone CRUD tests removed — that model is retired (V27). Postal-zone management is
    // now covered by AdminDeliveryConfigIT (PUT/GET /api/admin/postal-zones/{code}).

    private Cookie adminCookie(String email) throws Exception {
        AuthTestSupport.seedAdmin(users, encoder, email);
        return AuthTestSupport.loginAdmin(mvc, email, "JBSWY3DPEHPK3PXP");
    }
}
