package com.scanlanka.returns;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.scanlanka.AbstractIntegrationTest;
import com.scanlanka.auth.AuthTestSupport;
import com.scanlanka.auth.infra.AppUserRepository;
import com.scanlanka.catalog.app.ProductService;
import com.scanlanka.catalog.infra.ProductRepository;
import com.scanlanka.catalog.web.dto.ProductRequests.CreateProductRequest;
import com.scanlanka.order.domain.OrderStatus;
import com.scanlanka.order.infra.OrderItemRepository;
import com.scanlanka.order.infra.OrderRepository;
import com.scanlanka.returns.infra.RefundRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** After-sales: admin cancel/restock, refund cap + idempotency, no customer endpoints (16). */
class RefundIT extends AbstractIntegrationTest {

    private static final String MID = "REFMID";
    private static final String SECRET = "REFSECRET";

    @DynamicPropertySource
    static void payhere(DynamicPropertyRegistry r) {
        r.add("app.payhere.merchant-id", () -> MID);
        r.add("app.payhere.merchant-secret", () -> SECRET);
    }

    @Autowired MockMvc mvc;
    @Autowired ProductService productService;
    @Autowired ProductRepository products;
    @Autowired OrderRepository orders;
    @Autowired OrderItemRepository orderItems;
    @Autowired RefundRepository refunds;
    @Autowired AppUserRepository users;
    @Autowired PasswordEncoder encoder;
    @Autowired ObjectMapper objectMapper;

    @Test
    void adminCancelRestocksPaidOrder() throws Exception {
        long productId = createProduct(4);
        String orderNumber = placeAndPay(productId, "5.00");
        assertThat(products.findById(productId).orElseThrow().getStockQty()).isEqualTo(3);

        Cookie admin = adminCookie("cancel-restock@scanlanka.lk");
        mvc.perform(post("/api/admin/orders/" + orderNumber + "/cancel").cookie(admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"CUSTOMER_REQUEST\",\"password\":\"password123\"}"))
            .andExpect(status().isOk());

        assertThat(orders.findByOrderNumber(orderNumber).orElseThrow().getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(products.findById(productId).orElseThrow().getStockQty()).isEqualTo(4);
    }

    @Test
    void cancelRequiresStepUp() throws Exception {
        String orderNumber = placeAndPay(createProduct(2), "5.00");
        Cookie admin = adminCookie("cancel-stepup@scanlanka.lk");
        mvc.perform(post("/api/admin/orders/" + orderNumber + "/cancel").cookie(admin)
                .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"X\"}"))
            .andExpect(status().isForbidden());
    }

    @Test
    void cannotCancelShippedOrder() throws Exception {
        String orderNumber = placeAndPay(createProduct(2), "5.00");
        Cookie admin = adminCookie("cancel-shipped@scanlanka.lk");
        mvc.perform(post("/api/admin/orders/" + orderNumber + "/status").cookie(admin)
                .contentType(MediaType.APPLICATION_JSON).content("{\"to\":\"PACKED\"}"))
            .andExpect(status().isOk());
        mvc.perform(post("/api/admin/orders/" + orderNumber + "/status").cookie(admin)
                .contentType(MediaType.APPLICATION_JSON).content("{\"to\":\"SHIPPED\"}"))
            .andExpect(status().isOk());

        mvc.perform(post("/api/admin/orders/" + orderNumber + "/cancel").cookie(admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"LATE\",\"password\":\"password123\"}"))
            .andExpect(status().isConflict());
    }

    @Test
    void refundCappedIdempotentAndWriteOffSkipsRestock() throws Exception {
        long productId = createProduct(2);
        String orderNumber = placeAndPay(productId, "5.00");
        assertThat(products.findById(productId).orElseThrow().getStockQty()).isEqualTo(1);
        var order = orders.findByOrderNumber(orderNumber).orElseThrow();
        long itemId = orderItems.findByOrderId(order.getId()).getFirst().getId();
        Cookie admin = adminCookie("refund-cap@scanlanka.lk");

        String body = "{\"amountCents\":600,\"method\":\"PAYHERE\",\"reason\":\"DAMAGED\","
            + "\"idempotencyKey\":\"ref-test-1\",\"password\":\"password123\","
            + "\"items\":[{\"itemId\":" + itemId + ",\"quantity\":1,\"disposition\":\"WRITE_OFF\"}]}";
        mvc.perform(post("/api/admin/orders/" + orderNumber + "/refunds").cookie(admin)
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isUnprocessableEntity());

        String ok = "{\"amountCents\":250,\"method\":\"BANK\",\"reason\":\"PARTIAL\","
            + "\"gatewayRef\":\"BNK-1\",\"idempotencyKey\":\"ref-test-1\",\"password\":\"password123\","
            + "\"items\":[{\"itemId\":" + itemId + ",\"quantity\":1,\"disposition\":\"WRITE_OFF\"}]}";
        mvc.perform(post("/api/admin/orders/" + orderNumber + "/refunds").cookie(admin)
                .contentType(MediaType.APPLICATION_JSON).content(ok))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.amountCents").value(250));

        assertThat(products.findById(productId).orElseThrow().getStockQty()).isEqualTo(1);

        mvc.perform(post("/api/admin/orders/" + orderNumber + "/refunds").cookie(admin)
                .contentType(MediaType.APPLICATION_JSON).content(ok))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.amountCents").value(250));

        assertThat(refunds.findByOrderIdOrderByCreatedAtDesc(order.getId())).hasSize(1);
        assertThat(refunds.sumAmountByOrderId(order.getId())).isEqualTo(250);

        mvc.perform(get("/api/admin/orders/" + orderNumber + "/refunds").cookie(admin))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].method").value("BANK"));
    }

    @Test
    void restockRefundIncrementsStock() throws Exception {
        long productId = createProduct(3);
        String orderNumber = placeAndPay(productId, "5.00");
        var order = orders.findByOrderNumber(orderNumber).orElseThrow();
        long itemId = orderItems.findByOrderId(order.getId()).getFirst().getId();
        Cookie admin = adminCookie("refund-restock@scanlanka.lk");

        mvc.perform(post("/api/admin/orders/" + orderNumber + "/refunds").cookie(admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"amountCents\":250,\"method\":\"STORE_CREDIT\",\"idempotencyKey\":\"restock-1\","
                    + "\"password\":\"password123\",\"items\":[{\"itemId\":" + itemId
                    + ",\"quantity\":1,\"disposition\":\"RESTOCK\"}]}"))
            .andExpect(status().isOk());

        assertThat(products.findById(productId).orElseThrow().getStockQty()).isEqualTo(3);
    }

    private long createProduct(int stock) {
        return productService.create(new CreateProductRequest(
            null, "Return " + System.nanoTime(), null, null, null, "X", null, stock, 500L,
            List.of(), List.of()));
    }

    private String placeAndPay(long productId, String amount) throws Exception {
        MvcResult place = mvc.perform(post("/api/checkout").contentType(MediaType.APPLICATION_JSON)
                .content("{\"items\":[{\"productId\":" + productId + ",\"quantity\":1}],"
                    + "\"fulfilmentType\":\"PICKUP_SHOP\",\"deliveryPayment\":\"PREPAID\","
                    + "\"contactName\":\"R\",\"contactPhone\":\"+9477\",\"contactEmail\":\"r@x.lk\"}"))
            .andExpect(status().isOk()).andReturn();
        String orderNumber = objectMapper.readTree(place.getResponse().getContentAsString())
            .get("orderNumber").asText();
        String sig = gatewaySig(orderNumber, amount, "LKR", "2");
        mvc.perform(post("/api/payments/payhere/notify").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("merchant_id", MID).param("order_id", orderNumber)
                .param("payhere_amount", amount).param("payhere_currency", "LKR")
                .param("status_code", "2").param("md5sig", sig).param("payment_id", "PH-" + orderNumber))
            .andExpect(status().isOk());
        return orderNumber;
    }

    private Cookie adminCookie(String email) throws Exception {
        AuthTestSupport.seedAdmin(users, encoder, email);
        return AuthTestSupport.loginAdmin(mvc, email, "JBSWY3DPEHPK3PXP");
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
