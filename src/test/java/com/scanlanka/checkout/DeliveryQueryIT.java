package com.scanlanka.checkout;

import com.scanlanka.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Public delivery zone lookups (05 §3). */
class DeliveryQueryIT extends AbstractIntegrationTest {

    @Autowired MockMvc mvc;

    @Test
    void locationsAndPostalCodesArePublic() throws Exception {
        // base seeds postal_zone 00100 → lorry zone COLOMBO, district Colombo
        mvc.perform(get("/api/delivery/locations"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[?(@.zone == 'COLOMBO')].postalCodes[0]").value(org.hamcrest.Matchers.hasItem("00100")));

        mvc.perform(get("/api/delivery/postal-codes").param("q", "001"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[?(@.postalCode == '00100')]").exists());
    }
}
