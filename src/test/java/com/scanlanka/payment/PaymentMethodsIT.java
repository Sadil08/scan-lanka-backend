package com.scanlanka.payment;

import com.scanlanka.AbstractIntegrationTest;
import com.scanlanka.admin.app.SettingsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Public payment method toggles (06 FR-PAY-8). */
class PaymentMethodsIT extends AbstractIntegrationTest {

    @DynamicPropertySource
    static void payhere(DynamicPropertyRegistry r) {
        r.add("app.payhere.merchant-id", () -> "TESTMID");
    }

    @Autowired MockMvc mvc;
    @Autowired SettingsService settings;

    @Test
    void returnsConfiguredMethods() throws Exception {
        settings.put("bank_transfer_enabled", "false", null);
        settings.put("cod_enabled", "true", null);

        mvc.perform(get("/api/payments/methods"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.payhere").value(true))
            .andExpect(jsonPath("$.bankTransfer").value(false))
            .andExpect(jsonPath("$.deliveryCod").value(true));
    }
}
