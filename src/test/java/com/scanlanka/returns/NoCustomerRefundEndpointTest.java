package com.scanlanka.returns;

import com.scanlanka.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** SEC-RETURN-2: no customer-facing cancel/refund routes. */
class NoCustomerRefundEndpointTest extends AbstractIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired RequestMappingHandlerMapping handlerMapping;

    @Test
    void customerPathsReturnNotFound() throws Exception {
        mvc.perform(post("/api/orders/SL-TEST/cancel").contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isNotFound());
        mvc.perform(post("/api/orders/SL-TEST/refunds").contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isNotFound());
        mvc.perform(post("/api/orders/lookup/cancel").contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isNotFound());
    }

    @Test
    void refundMutationsOnlyUnderAdminOrders() {
        var paths = handlerMapping.getHandlerMethods().keySet().stream()
            .flatMap(info -> info.getPathPatternsCondition().getPatterns().stream())
            .map(Object::toString)
            .filter(p -> p.contains("cancel") || p.contains("refund"))
            .toList();
        assertThat(paths).isNotEmpty();
        assertThat(paths).allMatch(p -> p.startsWith("/api/admin/"));
    }
}
