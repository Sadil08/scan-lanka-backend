package com.scanlanka.admin;

import com.scanlanka.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Non-admin blocked across admin surface (08 SEC-ADMIN-1). */
class AdminAuthzIT extends AbstractIntegrationTest {

    @Autowired MockMvc mvc;

    @Test
    void unauthenticatedAdminRoutesForbidden() throws Exception {
        mvc.perform(get("/api/admin/dashboard")).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/orders")).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/delivery-zones")).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/settings")).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/delivery-config")).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/tax-config")).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/notifications")).andExpect(status().isForbidden());
        mvc.perform(put("/api/admin/tax-config").contentType("application/json").content("{}"))
            .andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/delivery-zones").contentType("application/json").content("{}"))
            .andExpect(status().isForbidden());
    }
}
