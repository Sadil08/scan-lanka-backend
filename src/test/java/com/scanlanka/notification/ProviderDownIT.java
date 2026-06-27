package com.scanlanka.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.scanlanka.AbstractIntegrationTest;
import com.scanlanka.catalog.app.ProductService;
import com.scanlanka.catalog.web.dto.ProductRequests.CreateProductRequest;
import com.scanlanka.notification.app.EmailProvider;
import com.scanlanka.notification.app.NotificationWorker;
import com.scanlanka.notification.infra.NotificationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Provider outage queues messages; worker retries until sent (10 AC-NOTIFY-3). */
class ProviderDownIT extends AbstractIntegrationTest {

    private static final String MID = "TESTMID";
    private static final String SECRET = "TESTSECRET";
    static final AtomicInteger SEND_ATTEMPTS = new AtomicInteger();

    @DynamicPropertySource
    static void payhere(DynamicPropertyRegistry r) {
        r.add("app.payhere.merchant-id", () -> MID);
        r.add("app.payhere.merchant-secret", () -> SECRET);
        // Neutralize the @Scheduled poll so this test deterministically controls draining via worker.drain();
        // otherwise the background poll races for the shared queue + SEND_ATTEMPTS counter.
        r.add("app.notifications.poll-ms", () -> "3600000");
    }

    @TestConfiguration
    static class FailOnceConfig {
        @Bean
        @Primary
        EmailProvider flakyProvider() {
            return (to, subject, body) -> {
                if (SEND_ATTEMPTS.incrementAndGet() == 1) {
                    throw new RuntimeException("provider down");
                }
            };
        }
    }

    @Autowired MockMvc mvc;
    @Autowired ProductService productService;
    @Autowired NotificationRepository notifications;
    @Autowired NotificationWorker worker;
    @Autowired ObjectMapper objectMapper;

    @Test
    void checkoutCompletesAndWorkerRetriesUntilSent() throws Exception {
        SEND_ATTEMPTS.set(0);
        Long productId = productService.create(new CreateProductRequest(
            null, "Marker " + System.nanoTime(), null, null, null, "Accessories", null, 5, 250L,
            List.of(), List.of()));

        var placeRes = mvc.perform(post("/api/checkout").contentType(MediaType.APPLICATION_JSON)
                .content("{\"items\":[{\"productId\":" + productId + ",\"quantity\":1}],"
                    + "\"deliveryMethod\":\"COMPANY_LORRY\",\"ship\":{\"street\":\"1 Main\",\"city\":\"Colombo\",\"province\":\"Western\",\"postalCode\":\"00100\"},"
                    + "\"contactName\":\"M\",\"contactPhone\":\"+9477\",\"contactEmail\":\"m@x.lk\"}"))
            .andExpect(status().isOk()).andReturn();
        String orderNumber = objectMapper.readTree(placeRes.getResponse().getContentAsString())
            .get("orderNumber").asText();

        String amount = "2.50";
        String sig = gatewaySig(orderNumber, amount, "LKR", "2");
        mvc.perform(post("/api/payments/payhere/notify").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("merchant_id", MID).param("order_id", orderNumber)
                .param("payhere_amount", amount).param("payhere_currency", "LKR")
                .param("status_code", "2").param("md5sig", sig).param("payment_id", "PH-D"))
            .andExpect(status().isOk());

        assertThat(notifications.findAll()).isNotEmpty();

        worker.drain();
        assertThat(notifications.findAll().stream().anyMatch(n -> "RETRYING".equals(n.getStatus()))).isTrue();

        worker.drain();
        assertThat(notifications.findAll().stream().allMatch(n -> "SENT".equals(n.getStatus()))).isTrue();
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
