package com.scanlanka.catalog;

import com.scanlanka.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Anti-scraping rate limit on public catalog reads (02-storefront-browse). */
class BrowseRateLimitIT extends AbstractIntegrationTest {

    @Autowired MockMvc mvc;

    @Test
    void browseRateLimitsAfterThreshold() throws Exception {
        for (int i = 0; i < 120; i++) {
            mvc.perform(get("/api/products")).andExpect(status().isOk());
        }
        mvc.perform(get("/api/products")).andExpect(status().isTooManyRequests());
    }
}
