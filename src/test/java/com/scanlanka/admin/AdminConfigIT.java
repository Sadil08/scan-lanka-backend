package com.scanlanka.admin;

import com.scanlanka.auth.AuthTestSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scanlanka.AbstractIntegrationTest;
import com.scanlanka.auth.infra.AppUserRepository;
import com.scanlanka.catalog.app.ProductService;
import com.scanlanka.catalog.domain.Product;
import com.scanlanka.catalog.infra.ProductRepository;
import com.scanlanka.catalog.web.dto.ProductRequests.CreateProductRequest;
import com.scanlanka.checkout.domain.BoardSizeTier;
import com.scanlanka.checkout.domain.CourierRateCard;
import com.scanlanka.checkout.domain.CourierZone;
import com.scanlanka.checkout.domain.LorryZone;
import com.scanlanka.checkout.domain.PostalZone;
import com.scanlanka.checkout.infra.CourierRateCardRepository;
import com.scanlanka.checkout.infra.PostalZoneRepository;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Delivery-config changes affect new quotes only; placed order snapshots immutable (08 AC-ADMIN-1/3). */
class AdminConfigIT extends AbstractIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ProductService productService;
    @Autowired ProductRepository products;
    @Autowired CourierRateCardRepository courierRates;
    @Autowired PostalZoneRepository postalZones;
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
        p.setBoardSizeTier(BoardSizeTier.BETWEEN_2FT_6FT);
        products.save(p);

        // Courier is only offered to OUTER areas (owner 2026-07-03) — quote/place from Kandy (OUTSTATION).
        if (!postalZones.existsById("20000")) {
            postalZones.save(new PostalZone("20000", LorryZone.OUTER, CourierZone.OUTSTATION,
                "Kandy", "Central Province"));
        }
        String checkout = "{\"items\":[{\"productId\":" + productId + ",\"quantity\":1}],"
            + "\"deliveryMethod\":\"COURIER\",\"postalCode\":\"20000\","
            + "\"ship\":{\"street\":\"1\",\"city\":\"Kandy\",\"province\":\"CP\",\"postalCode\":\"20000\"},"
            + "\"contactName\":\"A\",\"contactPhone\":\"+9477\",\"contactEmail\":\"a@x.lk\"}";

        long estimateBefore = quoteCourierEstimate(checkout);

        MvcResult placed = mvc.perform(post("/api/checkout").contentType(MediaType.APPLICATION_JSON).content(checkout))
            .andExpect(status().isOk()).andReturn();
        String orderNumber = objectMapper.readTree(placed.getResponse().getContentAsString()).get("orderNumber").asText();
        long snapshot = orders.findByOrderNumber(orderNumber).orElseThrow().getCourierEstimateCents();
        assertThat(snapshot).isEqualTo(estimateBefore);

        CourierRateCard r = courierRates.findById(CourierZone.OUTSTATION).orElseThrow();
        r.update(r.getFirstKgCents() + 50000, r.getAddlKgCents(), r.getHandlingOver2ftCents());
        courierRates.save(r);

        assertThat(quoteCourierEstimate(checkout)).isGreaterThan(estimateBefore);
        assertThat(orders.findByOrderNumber(orderNumber).orElseThrow().getCourierEstimateCents()).isEqualTo(snapshot);
    }

    private long quoteCourierEstimate(String body) throws Exception {
        MvcResult q = mvc.perform(post("/api/checkout/quote").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(q.getResponse().getContentAsString()).get("courierEstimateCents").asLong();
    }

    private Cookie adminCookie(String email) throws Exception {
        AuthTestSupport.seedAdmin(users, encoder, email);
        return AuthTestSupport.loginAdmin(mvc, email, "JBSWY3DPEHPK3PXP");
    }
}
