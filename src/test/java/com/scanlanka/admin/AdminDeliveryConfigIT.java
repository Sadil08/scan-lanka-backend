package com.scanlanka.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.scanlanka.AbstractIntegrationTest;
import com.scanlanka.auth.AuthTestSupport;
import com.scanlanka.auth.infra.AppUserRepository;
import com.scanlanka.catalog.app.ProductService;
import com.scanlanka.catalog.domain.Product;
import com.scanlanka.catalog.infra.ProductRepository;
import com.scanlanka.catalog.web.dto.ProductRequests.CreateProductRequest;
import com.scanlanka.checkout.domain.BoardSizeTier;
import com.scanlanka.checkout.domain.CourierZone;
import com.scanlanka.checkout.domain.LorryZone;
import com.scanlanka.checkout.domain.PostalZone;
import com.scanlanka.checkout.infra.PostalZoneRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Admin two-rail delivery config: courier rate card, min-bill, rail toggles, postal zones (08/17). */
class AdminDeliveryConfigIT extends AbstractIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired AppUserRepository users;
    @Autowired PasswordEncoder encoder;
    @Autowired ProductService productService;
    @Autowired ProductRepository products;
    @Autowired PostalZoneRepository postalZones;

    @Test
    void courierRateChangeReflectsInGetAndNewQuote() throws Exception {
        Cookie admin = admin("admin-rate@scanlanka.lk");
        mvc.perform(put("/api/admin/delivery-methods/COURIER").cookie(admin)
                .contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":true}"))
            .andExpect(status().isOk());
        // Weight-based Domex card (V48): first kg / additional kg / above-2ft handling, per area.
        mvc.perform(put("/api/admin/courier-rate-card/OUTSTATION").cookie(admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"firstKgCents\":60000,\"addlKgCents\":25000,\"handlingOver2ftCents\":160000}"))
            .andExpect(status().isOk());

        mvc.perform(get("/api/admin/courier-rate-card").cookie(admin))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[?(@.zone=='OUTSTATION')].firstKgCents")
                .value(org.hamcrest.Matchers.hasItem(60000)))
            .andExpect(jsonPath("$[?(@.zone=='OUTSTATION')].handlingOver2ftCents")
                .value(org.hamcrest.Matchers.hasItem(160000)));

        if (!postalZones.existsById("20000")) {
            postalZones.save(new PostalZone("20000", LorryZone.OUTER, CourierZone.OUTSTATION,
                "Kandy", "Central Province"));
        }
        Long id = productService.create(new CreateProductRequest(
            null, "RateBoard " + System.nanoTime(), null, null, null, "X", null, 10, 50000L,
            List.of(), List.of()));
        Product p = products.findById(id).orElseThrow();
        p.setBoardSizeTier(BoardSizeTier.BETWEEN_2FT_6FT);
        p.setWeightKg(new java.math.BigDecimal("3"));
        products.save(p);

        // 3 kg above 2 ft: Rs 600 + 2 x Rs 250 + Rs 1,600 handling = Rs 2,700
        mvc.perform(post("/api/checkout/quote").contentType(MediaType.APPLICATION_JSON)
                .content("{\"items\":[{\"productId\":" + id + ",\"quantity\":1}],"
                    + "\"deliveryMethod\":\"COURIER\",\"postalCode\":\"20000\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.courierEstimateCents").value(270000));
    }

    @Test
    void minBillUpdateRoundTrips() throws Exception {
        Cookie admin = admin("admin-minbill@scanlanka.lk");
        mvc.perform(put("/api/admin/delivery-settings").cookie(admin)
                .contentType(MediaType.APPLICATION_JSON).content("{\"lorryMinBillCents\":500000}"))
            .andExpect(status().isOk());
        mvc.perform(get("/api/admin/delivery-settings").cookie(admin))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.lorryMinBillCents").value(500000));
    }

    @Test
    void lorryCapSettingsRoundTrip() throws Exception {
        // owner 2026-07-07: unconditional cap on the whole Colombo/Suburb lorry total, any order size.
        Cookie admin = admin("admin-lorrycap@scanlanka.lk");
        mvc.perform(put("/api/admin/delivery-settings").cookie(admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"lorryCapColomboCents\":100000,\"lorryCapSuburbCents\":150000}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.lorryCapColomboCents").value(100000))
            .andExpect(jsonPath("$.lorryCapSuburbCents").value(150000));
        mvc.perform(get("/api/admin/delivery-settings").cookie(admin))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.lorryCapColomboCents").value(100000))
            .andExpect(jsonPath("$.lorryCapSuburbCents").value(150000));
    }

    @Test
    void railToggleRoundTrips() throws Exception {
        Cookie admin = admin("admin-toggle@scanlanka.lk");
        mvc.perform(put("/api/admin/delivery-methods/COURIER").cookie(admin)
                .contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":false}"))
            .andExpect(status().isOk());
        mvc.perform(get("/api/admin/delivery-methods").cookie(admin))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[?(@.method=='COURIER')].enabled").value(org.hamcrest.Matchers.hasItem(false)));
    }

    @Test
    void postalZoneUpsertRoundTrips() throws Exception {
        Cookie admin = admin("admin-postal@scanlanka.lk");
        mvc.perform(put("/api/admin/postal-zones/77700").cookie(admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"lorryZone\":\"OUTER\",\"courierZone\":\"OUTSTATION\",\"district\":\"Galle\",\"province\":\"Southern Province\"}"))
            .andExpect(status().isOk());
        mvc.perform(get("/api/admin/postal-zones/77700").cookie(admin))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.lorryZone").value("OUTER"))
            .andExpect(jsonPath("$.courierZone").value("OUTSTATION"));
    }

    @Test
    void deliveryConfigEditRequiresAdmin() throws Exception {
        mvc.perform(put("/api/admin/courier-rate-card/CITY_LIMITS").contentType(MediaType.APPLICATION_JSON)
                .content("{\"firstKgCents\":1,\"addlKgCents\":1,\"handlingOver2ftCents\":1}"))
            .andExpect(status().isForbidden());
    }

    private Cookie admin(String email) throws Exception {
        AuthTestSupport.seedAdmin(users, encoder, email);
        return AuthTestSupport.loginAdmin(mvc, email, "JBSWY3DPEHPK3PXP");
    }
}
