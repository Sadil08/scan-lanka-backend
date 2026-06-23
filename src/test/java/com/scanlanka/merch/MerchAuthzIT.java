package com.scanlanka.merch;

import com.scanlanka.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MerchAuthzIT extends AbstractIntegrationTest {

    @Autowired MockMvc mvc;

    @Test
    void nonAdminBlocked() throws Exception {
        mvc.perform(get("/api/admin/merch/featured")).andExpect(status().is4xxClientError());
        mvc.perform(get("/api/admin/merch/banners")).andExpect(status().is4xxClientError());
        mvc.perform(put("/api/admin/merch/featured").contentType("application/json").content("{\"items\":[]}"))
            .andExpect(status().is4xxClientError());
    }

    @Test
    void homeIsPublic() throws Exception {
        mvc.perform(get("/api/home")).andExpect(status().isOk());
    }
}
