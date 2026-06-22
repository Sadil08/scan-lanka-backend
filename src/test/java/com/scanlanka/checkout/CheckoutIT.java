package com.scanlanka.checkout;

import com.scanlanka.AbstractIntegrationTest;
import com.scanlanka.catalog.app.ProductService;
import com.scanlanka.catalog.web.dto.ProductRequests.CreateProductRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Checkout quote + place, end-to-end on real Postgres (05). Server computes all totals (SEC-PAY). */
class CheckoutIT extends AbstractIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ProductService productService;

    private Long seedProduct() {
        return productService.create(new CreateProductRequest(
            null, "Marker " + System.nanoTime(), null, null, null, "Accessories", null, 100, 250L,
            List.of(), List.of()));
    }

    @Test
    void quotePrepaidDeliveryComputesServerSideTotals() throws Exception {
        Long id = seedProduct();
        // seeded zone "Colombo Metro": base Rs500; standard item, no per-kg/pickpack/tax → delivery 50000
        mvc.perform(post("/api/checkout/quote").contentType(MediaType.APPLICATION_JSON)
                .content("{\"items\":[{\"productId\":" + id + ",\"quantity\":2}],"
                    + "\"fulfilmentType\":\"DELIVERY\",\"postalCode\":\"00100\",\"deliveryPayment\":\"PREPAID\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.subtotalCents").value(500))
            .andExpect(jsonPath("$.deliveryCents").value(50000))
            .andExpect(jsonPath("$.totalCents").value(50500))
            .andExpect(jsonPath("$.serviceable").value(true));
    }

    @Test
    void nonServiceablePostcode() throws Exception {
        Long id = seedProduct();
        mvc.perform(post("/api/checkout/quote").contentType(MediaType.APPLICATION_JSON)
                .content("{\"items\":[{\"productId\":" + id + ",\"quantity\":1}],"
                    + "\"fulfilmentType\":\"DELIVERY\",\"postalCode\":\"99999\",\"deliveryPayment\":\"PREPAID\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.serviceable").value(false));
    }

    @Test
    void codChargesProductOnlineDeliveryOnDelivery() throws Exception {
        Long id = seedProduct();
        mvc.perform(post("/api/checkout/quote").contentType(MediaType.APPLICATION_JSON)
                .content("{\"items\":[{\"productId\":" + id + ",\"quantity\":2}],"
                    + "\"fulfilmentType\":\"DELIVERY\",\"postalCode\":\"00100\",\"deliveryPayment\":\"COD\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalCents").value(500))            // product only online
            .andExpect(jsonPath("$.deliveryCodCents").value(50000));   // delivery on delivery
    }

    @Test
    void placeCreatesAnOrderWithSignedNumber() throws Exception {
        Long id = seedProduct();
        mvc.perform(post("/api/checkout").contentType(MediaType.APPLICATION_JSON)
                .content("{\"items\":[{\"productId\":" + id + ",\"quantity\":1}],"
                    + "\"fulfilmentType\":\"PICKUP_SHOP\",\"deliveryPayment\":\"PREPAID\","
                    + "\"contactName\":\"Mark\",\"contactPhone\":\"+94770000000\",\"contactEmail\":\"mark@x.lk\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.orderNumber").exists())
            .andExpect(jsonPath("$.totalCents").value(250));           // pickup → no delivery
    }
}
