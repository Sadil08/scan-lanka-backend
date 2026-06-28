package com.scanlanka.message;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scanlanka.AbstractIntegrationTest;
import com.scanlanka.auth.AuthTestSupport;
import com.scanlanka.auth.infra.AppUserRepository;
import com.scanlanka.catalog.app.ProductService;
import com.scanlanka.catalog.web.dto.ProductRequests.CreateProductRequest;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class OrderMessageIT extends AbstractIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ProductService productService;
    @Autowired AppUserRepository users;
    @Autowired PasswordEncoder encoder;
    @Autowired ObjectMapper objectMapper;

    private Long seedProduct() {
        return productService.create(new CreateProductRequest(
            null, "Msg Product " + System.nanoTime(), null, null, null, "X", null, 10, 300L,
            List.of(), List.of()));
    }

    @Test
    void threadOpensOnOrderCustomerAndAdminCanExchange() throws Exception {
        Long productId = seedProduct();
        Cookie customer = AuthTestSupport.loginVerifiedCustomer(mvc, users, "msgcust@scanlanka.lk");

        MvcResult placed = mvc.perform(post("/api/checkout").cookie(customer).contentType(MediaType.APPLICATION_JSON)
                .content("{\"items\":[{\"productId\":" + productId + ",\"quantity\":1}],"
                    + "\"deliveryMethod\":\"COMPANY_LORRY\",\"ship\":{\"street\":\"1 Main\",\"city\":\"Colombo\","
                    + "\"province\":\"Western\",\"postalCode\":\"00100\"},"
                    + "\"contactName\":\"Msg Cust\",\"contactPhone\":\"+94770000001\","
                    + "\"contactEmail\":\"msgcust@scanlanka.lk\"}"))
            .andExpect(status().isOk())
            .andReturn();

        JsonNode json = objectMapper.readTree(placed.getResponse().getContentAsString());
        String orderNumber = json.get("orderNumber").asText();

        mvc.perform(get("/api/orders/" + orderNumber + "/thread").cookie(customer))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.orderNumber").value(orderNumber))
            .andExpect(jsonPath("$.status").value("OPEN"))
            .andExpect(jsonPath("$.messages").isArray());

        mvc.perform(post("/api/orders/" + orderNumber + "/thread/messages").cookie(customer)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"body\":\"When will my goods arrive?\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.messages[0].body").value("When will my goods arrive?"));

        Cookie admin = adminCookie("msg-admin@scanlanka.lk");
        MvcResult inbox = mvc.perform(get("/api/admin/messages/threads").cookie(admin))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].orderNumber").value(orderNumber))
            .andReturn();

        long threadId = objectMapper.readTree(inbox.getResponse().getContentAsString())
            .get("content").get(0).get("id").asLong();

        mvc.perform(post("/api/admin/messages/threads/" + threadId + "/messages").cookie(admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"body\":\"We will deliver Thursday morning.\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.messages[1].role").value("ADMIN"));

        mvc.perform(get("/api/orders/" + orderNumber + "/thread").cookie(customer))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.messages.length()").value(2));
    }

    @Test
    void guestTokenScopedAndCrossCustomer404() throws Exception {
        Long productId = seedProduct();
        Cookie a = AuthTestSupport.loginVerifiedCustomer(mvc, users, "msga@scanlanka.lk");
        Cookie b = AuthTestSupport.loginVerifiedCustomer(mvc, users, "msgb@scanlanka.lk");

        MvcResult placed = mvc.perform(post("/api/checkout").cookie(a).contentType(MediaType.APPLICATION_JSON)
                .content("{\"items\":[{\"productId\":" + productId + ",\"quantity\":1}],"
                    + "\"deliveryMethod\":\"COMPANY_LORRY\",\"ship\":{\"street\":\"1 Main\",\"city\":\"Colombo\","
                    + "\"province\":\"Western\",\"postalCode\":\"00100\"},"
                    + "\"contactName\":\"A\",\"contactPhone\":\"+94770000001\","
                    + "\"contactEmail\":\"msga@scanlanka.lk\"}"))
            .andExpect(status().isOk())
            .andReturn();

        String orderNumber = objectMapper.readTree(placed.getResponse().getContentAsString())
            .get("orderNumber").asText();

        mvc.perform(get("/api/orders/" + orderNumber + "/thread").cookie(b))
            .andExpect(status().isNotFound());

        MvcResult tokenRes = mvc.perform(post("/api/orders/lookup/thread-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"orderNumber\":\"" + orderNumber + "\",\"email\":\"msga@scanlanka.lk\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accessToken").exists())
            .andReturn();

        String token = objectMapper.readTree(tokenRes.getResponse().getContentAsString())
            .get("accessToken").asText();

        mvc.perform(post("/api/orders/messages/" + token + "/messages").contentType(MediaType.APPLICATION_JSON)
                .content("{\"body\":\"Guest follow-up\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.messages[0].body").value("Guest follow-up"));
    }

    private Cookie adminCookie(String email) throws Exception {
        AuthTestSupport.seedAdmin(users, encoder, email);
        return AuthTestSupport.loginAdmin(mvc, email, "JBSWY3DPEHPK3PXP");
    }
}
