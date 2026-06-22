package com.scanlanka.payment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.scanlanka.AbstractIntegrationTest;
import com.scanlanka.catalog.app.ProductService;
import com.scanlanka.catalog.domain.Product;
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

/** PayHere webhook: verified → PAID + atomic decrement, exactly once on replay (06 AC-PAY-2/3/4). */
class PaymentWebhookIT extends AbstractIntegrationTest {

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
    void verifiedNotifyMarksPaidAndDecrementsOnce() throws Exception {
        Long productId = productService.create(new CreateProductRequest(
            null, "Marker " + System.nanoTime(), null, null, null, "Accessories", null, 5, 250L,
            List.of(), List.of()));

        // place a pickup order (total = Rs2.50)
        var placeRes = mvc.perform(post("/api/checkout").contentType(MediaType.APPLICATION_JSON)
                .content("{\"items\":[{\"productId\":" + productId + ",\"quantity\":1}],"
                    + "\"fulfilmentType\":\"PICKUP_SHOP\",\"deliveryPayment\":\"PREPAID\","
                    + "\"contactName\":\"Mark\",\"contactPhone\":\"+9477\",\"contactEmail\":\"m@x.lk\"}"))
            .andExpect(status().isOk()).andReturn();
        String orderNumber = objectMapper.readTree(placeRes.getResponse().getContentAsString())
            .get("orderNumber").asText();

        String amount = "2.50";
        String sig = gatewaySig(orderNumber, amount, "LKR", "2");

        // deliver the SAME notify twice
        for (int i = 0; i < 2; i++) {
            mvc.perform(post("/api/payments/payhere/notify")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .param("merchant_id", MID).param("order_id", orderNumber)
                    .param("payhere_amount", amount).param("payhere_currency", "LKR")
                    .param("status_code", "2").param("md5sig", sig).param("payment_id", "PH123"))
                .andExpect(status().isOk());
        }

        assertThat(orders.findByOrderNumber(orderNumber).orElseThrow().getStatus()).isEqualTo(OrderStatus.PAID);
        // stock decremented exactly once: 5 → 4 (not 3)
        Product p = products.findById(productId).orElseThrow();
        assertThat(p.getStockQty()).isEqualTo(4);
    }

    @Test
    void forgedSignatureDoesNotMarkPaid() throws Exception {
        Long productId = productService.create(new CreateProductRequest(
            null, "Marker " + System.nanoTime(), null, null, null, "Accessories", null, 5, 250L,
            List.of(), List.of()));
        var placeRes = mvc.perform(post("/api/checkout").contentType(MediaType.APPLICATION_JSON)
                .content("{\"items\":[{\"productId\":" + productId + ",\"quantity\":1}],"
                    + "\"fulfilmentType\":\"PICKUP_SHOP\",\"deliveryPayment\":\"PREPAID\","
                    + "\"contactName\":\"M\",\"contactPhone\":\"+9477\",\"contactEmail\":\"m@x.lk\"}"))
            .andExpect(status().isOk()).andReturn();
        String orderNumber = objectMapper.readTree(placeRes.getResponse().getContentAsString())
            .get("orderNumber").asText();

        mvc.perform(post("/api/payments/payhere/notify").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("merchant_id", MID).param("order_id", orderNumber)
                .param("payhere_amount", "2.50").param("payhere_currency", "LKR")
                .param("status_code", "2").param("md5sig", "FORGED").param("payment_id", "PH9"))
            .andExpect(status().isOk());

        assertThat(orders.findByOrderNumber(orderNumber).orElseThrow().getStatus())
            .isEqualTo(OrderStatus.PENDING_PAYMENT); // not paid
    }

    @Test
    void amountMismatchDoesNotMarkPaid() throws Exception {
        Long productId = productService.create(new CreateProductRequest(
            null, "Marker " + System.nanoTime(), null, null, null, "Accessories", null, 5, 250L,
            List.of(), List.of()));
        var placeRes = mvc.perform(post("/api/checkout").contentType(MediaType.APPLICATION_JSON)
                .content("{\"items\":[{\"productId\":" + productId + ",\"quantity\":1}],"
                    + "\"fulfilmentType\":\"PICKUP_SHOP\",\"deliveryPayment\":\"PREPAID\","
                    + "\"contactName\":\"M\",\"contactPhone\":\"+9477\",\"contactEmail\":\"m@x.lk\"}"))
            .andExpect(status().isOk()).andReturn();
        String orderNumber = objectMapper.readTree(placeRes.getResponse().getContentAsString())
            .get("orderNumber").asText();

        String wrongAmount = "99.99";
        String sig = gatewaySig(orderNumber, wrongAmount, "LKR", "2");
        mvc.perform(post("/api/payments/payhere/notify").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("merchant_id", MID).param("order_id", orderNumber)
                .param("payhere_amount", wrongAmount).param("payhere_currency", "LKR")
                .param("status_code", "2").param("md5sig", sig).param("payment_id", "PH-BAD"))
            .andExpect(status().isOk());

        assertThat(orders.findByOrderNumber(orderNumber).orElseThrow().getStatus())
            .isEqualTo(OrderStatus.PENDING_PAYMENT);
        assertThat(products.findById(productId).orElseThrow().getStockQty()).isEqualTo(5);
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
