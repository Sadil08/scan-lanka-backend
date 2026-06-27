package com.scanlanka.admin;

import com.scanlanka.auth.AuthTestSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scanlanka.AbstractIntegrationTest;
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

/** Admin orders board: list/detail/status/dispatch/dashboard + role gate (08 AC-ADMIN-5/6). */
class AdminOrderIT extends AbstractIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ProductService productService;
    @Autowired AppUserRepository users;
    @Autowired PasswordEncoder encoder;
    @Autowired ObjectMapper objectMapper;

    private Cookie adminCookie(String email) throws Exception {
        AuthTestSupport.seedAdmin(users, encoder, email);
        return AuthTestSupport.loginAdmin(mvc, email, "JBSWY3DPEHPK3PXP");
    }

    private String placeOrder() throws Exception {
        Long productId = productService.create(new CreateProductRequest(
            null, "Marker " + System.nanoTime(), null, null, null, "Accessories", null, 5, 250L,
            List.of(), List.of()));
        MvcResult res = mvc.perform(post("/api/checkout").contentType(MediaType.APPLICATION_JSON)
                .content("{\"items\":[{\"productId\":" + productId + ",\"quantity\":1}],"
                    + "\"deliveryMethod\":\"COMPANY_LORRY\",\"ship\":{\"street\":\"1 Main\",\"city\":\"Colombo\",\"province\":\"Western\",\"postalCode\":\"00100\"},"
                    + "\"contactName\":\"Mark\",\"contactPhone\":\"+9477\",\"contactEmail\":\"m@x.lk\"}"))
            .andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString()).get("orderNumber").asText();
    }

    @Test
    void adminManagesOrders() throws Exception {
        String orderNumber = placeOrder();
        Cookie admin = adminCookie("admin-orders@scanlanka.lk");

        mvc.perform(get("/api/admin/orders").cookie(admin))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content").isArray());

        mvc.perform(get("/api/admin/orders/" + orderNumber).cookie(admin))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("PENDING_PAYMENT"));

        // legal transition PENDING_PAYMENT → CANCELLED
        mvc.perform(post("/api/admin/orders/" + orderNumber + "/status").cookie(admin)
                .contentType(MediaType.APPLICATION_JSON).content("{\"to\":\"CANCELLED\",\"note\":\"test\"}"))
            .andExpect(status().isOk());
        mvc.perform(get("/api/admin/orders/" + orderNumber).cookie(admin))
            .andExpect(jsonPath("$.status").value("CANCELLED"));

        mvc.perform(get("/api/admin/orders/" + orderNumber + "/dispatch-summary").cookie(admin))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.lines").isArray());

        mvc.perform(get("/api/admin/orders/dashboard").cookie(admin)).andExpect(status().isOk());
    }

    @Test
    void illegalTransitionRejected() throws Exception {
        String orderNumber = placeOrder();
        Cookie admin = adminCookie("admin-illegal@scanlanka.lk");
        mvc.perform(post("/api/admin/orders/" + orderNumber + "/status").cookie(admin)
                .contentType(MediaType.APPLICATION_JSON).content("{\"to\":\"COMPLETED\"}"))
            .andExpect(status().isConflict()); // PENDING_PAYMENT → COMPLETED is illegal
    }

    @Test
    void adminListsCustomerOrders() throws Exception {
        String email = "cust-orders-" + System.nanoTime() + "@x.lk";
        Cookie customer = AuthTestSupport.loginVerifiedCustomer(mvc, users, email);
        long customerId = users.findByEmailIgnoreCase(email).orElseThrow().getId();

        Long productId = productService.create(new CreateProductRequest(
            null, "Cust " + System.nanoTime(), null, null, null, "Accessories", null, 5, 250L,
            List.of(), List.of()));
        MvcResult res = mvc.perform(post("/api/checkout").cookie(customer).contentType(MediaType.APPLICATION_JSON)
                .content("{\"items\":[{\"productId\":" + productId + ",\"quantity\":1}],"
                    + "\"deliveryMethod\":\"COMPANY_LORRY\",\"ship\":{\"street\":\"1 Main\",\"city\":\"Colombo\",\"province\":\"Western\",\"postalCode\":\"00100\"},"
                    + "\"contactName\":\"Mark\",\"contactPhone\":\"+9477\",\"contactEmail\":\"" + email + "\"}"))
            .andExpect(status().isOk()).andReturn();
        String orderNumber = objectMapper.readTree(res.getResponse().getContentAsString()).get("orderNumber").asText();

        Cookie admin = adminCookie("admin-cust-orders@scanlanka.lk");
        mvc.perform(get("/api/admin/orders/customers/" + customerId).cookie(admin))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].orderNumber").value(orderNumber));
    }

    @Test
    void nonAdminBlocked() throws Exception {
        mvc.perform(get("/api/admin/orders")).andExpect(status().is4xxClientError());
    }
}
