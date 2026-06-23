package com.scanlanka.geo;

import com.scanlanka.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Geo + FX display (13 AC-GEO-1/2). */
class GeoIT extends AbstractIntegrationTest {

    @Autowired MockMvc mvc;

    @Test
    void sriLankaCanCheckout() throws Exception {
        mvc.perform(get("/api/geo").param("country", "LK"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.country").value("LK"))
            .andExpect(jsonPath("$.currency").value("LKR"))
            .andExpect(jsonPath("$.canCheckout").value(true))
            .andExpect(jsonPath("$.indicativePricing").value(false));
    }

    @Test
    void internationalIndicativeNoCheckout() throws Exception {
        mvc.perform(get("/api/geo").param("country", "US"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.country").value("US"))
            .andExpect(jsonPath("$.currency").value("USD"))
            .andExpect(jsonPath("$.canCheckout").value(false))
            .andExpect(jsonPath("$.indicativePricing").value(true));
    }

    @Test
    void fxRatesCached() throws Exception {
        mvc.perform(get("/api/fx-rates"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.base").value("LKR"))
            .andExpect(jsonPath("$.rates.USD").exists());
    }
}
