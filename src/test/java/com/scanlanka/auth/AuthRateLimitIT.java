package com.scanlanka.auth;

import com.scanlanka.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Auth endpoint rate limiting (07 AuthRateLimitIT, FR-AUTH-13). */
class AuthRateLimitIT extends AbstractIntegrationTest {

    @Autowired MockMvc mvc;

    @Test
    void registerRateLimitsAfterThreshold() throws Exception {
        for (int i = 0; i < 10; i++) {
            mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"email\":\"rl" + i + "@scanlanka.lk\",\"password\":\"password123\",\"name\":\"R\"}"))
                .andExpect(status().isCreated());
        }
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"rl-over@scanlanka.lk\",\"password\":\"password123\",\"name\":\"R\"}"))
            .andExpect(status().isTooManyRequests());
    }
}
