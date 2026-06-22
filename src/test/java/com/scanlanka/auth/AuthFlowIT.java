package com.scanlanka.auth;

import com.scanlanka.AbstractIntegrationTest;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end auth flow (07 AuthFlowIT / CookieSecurityIT). Proves: register → login sets an httpOnly,
 * SameSite=Strict cookie (not JS-readable) → /me authenticates from the cookie → logout-all invalidates.
 */
class AuthFlowIT extends AbstractIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Test
    void registerLoginMeLogoutAll() throws Exception {
        String email = "flow@scanlanka.lk";

        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"password123\",\"name\":\"Flow\"}"))
            .andExpect(status().isCreated());

        MvcResult login = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"password123\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.email").value(email))
            .andExpect(jsonPath("$.role").value("CUSTOMER"))
            .andReturn();

        // Cookie is httpOnly + SameSite=Strict (token not JS-readable — global/02 §2, AC-AUTH-2)
        String setCookie = String.join(";", login.getResponse().getHeaders("Set-Cookie"));
        assertThat(setCookie).contains("HttpOnly").contains("SameSite=Strict");

        Cookie access = login.getResponse().getCookie("sl_at");
        assertThat(access).isNotNull();

        mvc.perform(get("/api/auth/me").cookie(access))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.email").value(email));

        mvc.perform(post("/api/auth/logout-all").cookie(access))
            .andExpect(status().isOk());

        // After logout-all the token_version bumped → the old access cookie is now rejected
        mvc.perform(get("/api/auth/me").cookie(access))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsRoleFromRequestBody() throws Exception {
        // Mass-assignment guard: a 'role' field in the body is ignored (AC-AUTH-3)
        String email = "noadmin@scanlanka.lk";
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"password123\",\"name\":\"X\",\"role\":\"ADMIN\"}"))
            .andExpect(status().isCreated());

        MvcResult login = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"password123\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.role").value("CUSTOMER")) // not ADMIN
            .andReturn();
        assertThat(login.getResponse().getCookie("sl_at")).isNotNull();
    }
}
