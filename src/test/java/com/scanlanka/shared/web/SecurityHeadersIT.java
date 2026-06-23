package com.scanlanka.shared.web;

import com.scanlanka.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Transport security headers on API responses (global/05 T-13b). */
class SecurityHeadersIT extends AbstractIntegrationTest {

    @Autowired MockMvc mvc;

    @Test
    void apiResponsesIncludeHardeningHeaders() throws Exception {
        var res = mvc.perform(get("/api/ping")).andExpect(status().isOk()).andReturn().getResponse();
        assertThat(res.getHeader("Content-Security-Policy")).contains("default-src 'none'");
        assertThat(res.getHeader("Strict-Transport-Security")).contains("max-age");
        assertThat(res.getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(res.getHeader("X-Frame-Options")).isEqualTo("DENY");
        assertThat(res.getHeader("Referrer-Policy")).isEqualTo("no-referrer");
    }
}
