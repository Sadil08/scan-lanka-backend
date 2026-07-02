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

    @Test
    void courierRateChangeReflectsInGetAndNewQuote() throws Exception {
        Cookie admin = admin("admin-rate@scanlanka.lk");
        mvc.perform(put("/api/admin/delivery-methods/COURIER").cookie(admin)
                .contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":true}"))
            .andExpect(status().isOk());
        mvc.perform(put("/api/admin/courier-rate-card/CITY_LIMITS/BETWEEN_2FT_6FT").cookie(admin)
                .contentType(MediaType.APPLICATION_JSON).content("{\"flatCents\":120000}"))
            .andExpect(status().isOk());

        mvc.perform(get("/api/admin/courier-rate-card").cookie(admin))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[?(@.zone=='CITY_LIMITS' && @.sizeTier=='BETWEEN_2FT_6FT')].flatCents")
                .value(org.hamcrest.Matchers.hasItem(120000)));

        Long id = productService.create(new CreateProductRequest(
            null, "RateBoard " + System.nanoTime(), null, null, null, "X", null, 10, 50000L,
            List.of(), List.of()));
        Product p = products.findById(id).orElseThrow();
        p.setBoardSizeTier(BoardSizeTier.BETWEEN_2FT_6FT);
        products.save(p);

        mvc.perform(post("/api/checkout/quote").contentType(MediaType.APPLICATION_JSON)
                .content("{\"items\":[{\"productId\":" + id + ",\"quantity\":1}],"
                    + "\"deliveryMethod\":\"COURIER\",\"postalCode\":\"00100\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.courierEstimateCents").value(120000));
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
        mvc.perform(put("/api/admin/courier-rate-card/CITY_LIMITS/BETWEEN_2FT_6FT").contentType(MediaType.APPLICATION_JSON)
                .content("{\"flatCents\":1}"))
            .andExpect(status().isForbidden());
    }

    private Cookie admin(String email) throws Exception {
        AuthTestSupport.seedAdmin(users, encoder, email);
        return AuthTestSupport.loginAdmin(mvc, email, "JBSWY3DPEHPK3PXP");
    }
}
