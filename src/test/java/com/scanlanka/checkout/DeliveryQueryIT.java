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
        // base seeds postal_zone 00100 → lorry zone COLOMBO, district Colombo. Locations are summarized
        // as district + count (not a flat postal-code dump — the real set runs to ~1,800 codes, 17).
        mvc.perform(get("/api/delivery/locations"))
            .andExpect(status().isOk())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                .string(org.hamcrest.Matchers.allOf(
                    org.hamcrest.Matchers.containsString("\"zone\":\"COLOMBO\""),
                    org.hamcrest.Matchers.containsString("\"district\":\"Colombo\""))));

        mvc.perform(get("/api/delivery/postal-codes").param("q", "001"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[?(@.postalCode == '00100')]").exists());
    }
}
