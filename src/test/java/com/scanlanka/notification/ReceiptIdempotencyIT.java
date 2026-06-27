package com.scanlanka.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.scanlanka.AbstractIntegrationTest;
import com.scanlanka.catalog.app.ProductService;
import com.scanlanka.catalog.web.dto.ProductRequests.CreateProductRequest;
import com.scanlanka.notification.infra.NotificationRepository;
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

/** Webhook replay enqueues exactly one receipt (10 AC-NOTIFY-1). */
class ReceiptIdempotencyIT extends AbstractIntegrationTest {

    private static final String MID = "TESTMID";
    private static final String SECRET = "TESTSECRET";

    @DynamicPropertySource
    static void payhere(DynamicPropertyRegistry r) {
        r.add("app.payhere.merchant-id", () -> MID);
        r.add("app.payhere.merchant-secret", () -> SECRET);
    }

    @Autowired MockMvc mvc;
    @Autowired ProductService productService;
    @Autowired OrderRepository orders;
    @Autowired NotificationRepository notifications;
    @Autowired ObjectMapper objectMapper;

    @Test
    void replayedWebhookEnqueuesOneReceipt() throws Exception {
        Long productId = productService.create(new CreateProductRequest(
            null, "Marker " + System.nanoTime(), null, null, null, "Accessories", null, 5, 250L,
            List.of(), List.of()));

        var placeRes = mvc.perform(post("/api/checkout").contentType(MediaType.APPLICATION_JSON)
                .content("{\"items\":[{\"productId\":" + productId + ",\"quantity\":1}],"
                    + "\"deliveryMethod\":\"COMPANY_LORRY\",\"ship\":{\"street\":\"1 Main\",\"city\":\"Colombo\",\"province\":\"Western\",\"postalCode\":\"00100\"},"
                    + "\"contactName\":\"Mark\",\"contactPhone\":\"+9477\",\"contactEmail\":\"m@x.lk\"}"))
            .andExpect(status().isOk()).andReturn();
        String orderNumber = objectMapper.readTree(placeRes.getResponse().getContentAsString())
            .get("orderNumber").asText();
        long orderId = orders.findByOrderNumber(orderNumber).orElseThrow().getId();

        String amount = "2.50";
        String sig = gatewaySig(orderNumber, amount, "LKR", "2");
        for (int i = 0; i < 2; i++) {
            mvc.perform(post("/api/payments/payhere/notify").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .param("merchant_id", MID).param("order_id", orderNumber)
                    .param("payhere_amount", amount).param("payhere_currency", "LKR")
                    .param("status_code", "2").param("md5sig", sig).param("payment_id", "PH-R"))
                .andExpect(status().isOk());
        }

        assertThat(notifications.existsByIdempotencyKey("receipt:" + orderId)).isTrue();
        assertThat(notifications.findAll().stream().filter(n -> "ORDER_RECEIPT".equals(n.getType())).count())
            .isEqualTo(1);
        assertThat(notifications.findAll().stream().filter(n -> "ADMIN_DISPATCH".equals(n.getType())).count())
            .isEqualTo(1);
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
