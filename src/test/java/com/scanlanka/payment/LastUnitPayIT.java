package com.scanlanka.payment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.scanlanka.AbstractIntegrationTest;
import com.scanlanka.catalog.app.ProductService;
import com.scanlanka.catalog.infra.ProductRepository;
import com.scanlanka.catalog.web.dto.ProductRequests.CreateProductRequest;
import com.scanlanka.order.domain.OrderStatus;
import com.scanlanka.order.infra.OrderRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Last unit: one checkout reserves, second fails; pay succeeds with no oversell (06 T16). */
class LastUnitPayIT extends AbstractIntegrationTest {

    private static final String MID = "TESTMID";
    private static final String SECRET = "TESTSECRET";

    @DynamicPropertySource
    static void payhere(DynamicPropertyRegistry r) {
        r.add("app.payhere.merchant-id", () -> MID);
        r.add("app.payhere.merchant-secret", () -> SECRET);
    }

    @Autowired MockMvc mvc;
    @Autowired ProductService productService;
    @Autowired ProductRepository products;
    @Autowired OrderRepository orders;
    @Autowired ObjectMapper objectMapper;

    @Test
    void paySucceedsAfterLastUnitReservedAndSecondCheckoutFails() throws Exception {
        Long productId = productService.create(new CreateProductRequest(
            null, "Last One " + System.nanoTime(), null, null, null, "X", null, 1, 500L,
            List.of(), List.of()));

        String body = "{\"items\":[{\"productId\":" + productId + ",\"quantity\":1}],"
            + "\"fulfilmentType\":\"PICKUP_SHOP\",\"deliveryPayment\":\"PREPAID\","
            + "\"contactName\":\"X\",\"contactPhone\":\"+94770000000\",\"contactEmail\":\"x@y.lk\"}";

        var placeRes = mvc.perform(post("/api/checkout").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isOk()).andReturn();
        String orderNumber = objectMapper.readTree(placeRes.getResponse().getContentAsString())
            .get("orderNumber").asText();

        mvc.perform(post("/api/checkout").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isConflict());

        String amount = "5.00";
        String sig = gatewaySig(orderNumber, amount, "LKR", "2");
        mvc.perform(post("/api/payments/payhere/notify").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("merchant_id", MID).param("order_id", orderNumber)
                .param("payhere_amount", amount).param("payhere_currency", "LKR")
                .param("status_code", "2").param("md5sig", sig).param("payment_id", "PH-LAST"))
            .andExpect(status().isOk());

        assertThat(orders.findByOrderNumber(orderNumber).orElseThrow().getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(products.findById(productId).orElseThrow().getStockQty()).isZero();
    }

    private static String gatewaySig(String orderRef, String amount, String currency, String status) {
        return md5Upper(MID + orderRef + amount + currency + status + md5Upper(SECRET));
    }

    private static String md5Upper(String s) {
        try {
            return HexFormat.of().withUpperCase()
                .formatHex(MessageDigest.getInstance("MD5").digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
