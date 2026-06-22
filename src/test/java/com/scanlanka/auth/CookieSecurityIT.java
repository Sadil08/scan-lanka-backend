package com.scanlanka.auth;

import com.scanlanka.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** httpOnly + SameSite cookie contract (07 CookieSecurityIT, AC-AUTH-2). */
class CookieSecurityIT extends AbstractIntegrationTest {

    @Autowired MockMvc mvc;

    @Test
    void loginSetsHttpOnlySameSiteCookiesAndOmitsTokenFromBody() throws Exception {
        String email = "cookie-" + System.nanoTime() + "@scanlanka.lk";
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
            .content("{\"email\":\"" + email + "\",\"password\":\"password123\",\"name\":\"C\"}"))
            .andExpect(status().isCreated());

        var login = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"password123\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accessToken").doesNotExist())
            .andExpect(jsonPath("$.token").doesNotExist())
            .andReturn();

        String setCookie = String.join(";", login.getResponse().getHeaders("Set-Cookie"));
        assertThat(setCookie).contains("HttpOnly").contains("SameSite=Strict");
        assertThat(login.getResponse().getCookie("sl_at")).isNotNull();
        assertThat(login.getResponse().getCookie("sl_rt")).isNotNull();
    }
}
