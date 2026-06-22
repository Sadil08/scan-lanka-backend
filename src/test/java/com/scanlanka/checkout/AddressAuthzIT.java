package com.scanlanka.checkout;

import com.scanlanka.auth.AuthTestSupport;
import com.scanlanka.AbstractIntegrationTest;
import com.scanlanka.auth.infra.AppUserRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Address ownership isolation (05 AddressAuthzIT). */
class AddressAuthzIT extends AbstractIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired AppUserRepository users;

    @Test
    void addressesAreCustomerScoped() throws Exception {
        Cookie a = AuthTestSupport.loginVerifiedCustomer(mvc, users, "addra@scanlanka.lk");
        Cookie b = AuthTestSupport.loginVerifiedCustomer(mvc, users, "addrb@scanlanka.lk");

        MvcResult created = mvc.perform(post("/api/addresses").cookie(a).contentType(MediaType.APPLICATION_JSON)
                .content("{\"street\":\"1 Main\",\"city\":\"Colombo\",\"province\":\"Western\","
                    + "\"postalCode\":\"00100\",\"phone\":\"+94770000000\",\"email\":\"addra@scanlanka.lk\",\"isDefault\":true}"))
            .andExpect(status().isOk())
            .andReturn();

        String id = created.getResponse().getContentAsString().replaceAll(".*\"id\"\\s*:\\s*(\\d+).*", "$1");

        mvc.perform(delete("/api/addresses/" + id).cookie(b))
            .andExpect(status().isNotFound());
    }
}
