package com.scanlanka.payment;

import com.scanlanka.auth.AuthTestSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scanlanka.AbstractIntegrationTest;
import com.scanlanka.auth.infra.AppUserRepository;
import com.scanlanka.catalog.app.ProductService;
import com.scanlanka.catalog.domain.Product;
import com.scanlanka.catalog.infra.ProductRepository;
import com.scanlanka.catalog.web.dto.ProductRequests.CreateProductRequest;
import com.scanlanka.order.domain.OrderStatus;
import com.scanlanka.order.infra.OrderRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Bank transfer: upload slip → AWAITING → admin manual confirm → PAID + decrement (06 AC-PAY-7/8). */
class BankTransferIT extends AbstractIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ProductService productService;
    @Autowired ProductRepository products;
    @Autowired OrderRepository orders;
    @Autowired AppUserRepository users;
    @Autowired PasswordEncoder encoder;
    @Autowired ObjectMapper objectMapper;

    private Long seed() {
        return productService.create(new CreateProductRequest(
            null, "Marker " + System.nanoTime(), null, null, null, "Accessories", null, 5, 250L,
            List.of(), List.of()));
    }

    private String place(Long productId) throws Exception {
        MvcResult res = mvc.perform(post("/api/checkout").contentType(MediaType.APPLICATION_JSON)
                .content("{\"items\":[{\"productId\":" + productId + ",\"quantity\":1}],"
                    + "\"fulfilmentType\":\"PICKUP_SHOP\",\"deliveryPayment\":\"PREPAID\","
                    + "\"contactName\":\"M\",\"contactPhone\":\"+9477\",\"contactEmail\":\"m@x.lk\"}"))
            .andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString()).get("orderNumber").asText();
    }

    private Cookie adminCookie(String email) throws Exception {
        AuthTestSupport.seedAdmin(users, encoder, email);
        return AuthTestSupport.loginAdmin(mvc, email, "JBSWY3DPEHPK3PXP");
    }

    private static MockMultipartFile slip() throws Exception {
        BufferedImage img = new BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        return new MockMultipartFile("file", "slip.png", "image/png", out.toByteArray());
    }

    @Test
    void uploadThenAdminConfirmMarksPaidAndDecrements() throws Exception {
        Long productId = seed();
        String orderNumber = place(productId);

        mvc.perform(multipart("/api/payments/bank-transfer/slip").file(slip()).param("orderNumber", orderNumber).param("email", "m@x.lk"))
            .andExpect(status().isCreated());
        assertThat(orders.findByOrderNumber(orderNumber).orElseThrow().getStatus())
            .isEqualTo(OrderStatus.AWAITING_BANK_CONFIRMATION);

        Cookie admin = adminCookie("admin-bt@scanlanka.lk");
        mvc.perform(post("/api/admin/payments/" + orderNumber + "/bank-confirm").cookie(admin)
                .contentType(MediaType.APPLICATION_JSON).content("{\"note\":\"verified\"}"))
            .andExpect(status().isOk());

        assertThat(orders.findByOrderNumber(orderNumber).orElseThrow().getStatus()).isEqualTo(OrderStatus.PAID);
        Product p = products.findById(productId).orElseThrow();
        assertThat(p.getStockQty()).isEqualTo(4);
    }

    @Test
    void nonAdminCannotConfirm() throws Exception {
        String orderNumber = place(seed());
        mvc.perform(multipart("/api/payments/bank-transfer/slip").file(slip()).param("orderNumber", orderNumber).param("email", "m@x.lk"))
            .andExpect(status().isCreated());
        // no admin cookie → role-gated → 4xx
        mvc.perform(post("/api/admin/payments/" + orderNumber + "/bank-confirm")
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().is4xxClientError());
    }

    @Test
    void rejectThenReuploadSlip() throws Exception {
        Long productId = seed();
        String orderNumber = place(productId);
        mvc.perform(multipart("/api/payments/bank-transfer/slip").file(slip()).param("orderNumber", orderNumber).param("email", "m@x.lk"))
            .andExpect(status().isCreated());

        Cookie admin = adminCookie("admin-reject@scanlanka.lk");
        mvc.perform(post("/api/admin/payments/" + orderNumber + "/bank-reject").cookie(admin)
                .contentType(MediaType.APPLICATION_JSON).content("{\"note\":\"unclear\"}"))
            .andExpect(status().isOk());
        assertThat(orders.findByOrderNumber(orderNumber).orElseThrow().getStatus())
            .isEqualTo(OrderStatus.BANK_SLIP_REJECTED);

        mvc.perform(multipart("/api/payments/bank-transfer/slip").file(slip()).param("orderNumber", orderNumber).param("email", "m@x.lk"))
            .andExpect(status().isCreated());
        assertThat(orders.findByOrderNumber(orderNumber).orElseThrow().getStatus())
            .isEqualTo(OrderStatus.AWAITING_BANK_CONFIRMATION);
    }

    /** P0-3: a slip upload for a guest order with the wrong (or no) email must be rejected. */
    @Test
    void cannotUploadSlipForSomeoneElsesOrder() throws Exception {
        String orderNumber = place(seed());

        // wrong email → 404 (no existence oracle), order stays PENDING_PAYMENT
        mvc.perform(multipart("/api/payments/bank-transfer/slip").file(slip())
                .param("orderNumber", orderNumber).param("email", "attacker@evil.lk"))
            .andExpect(status().isNotFound());
        // missing email → also rejected
        mvc.perform(multipart("/api/payments/bank-transfer/slip").file(slip())
                .param("orderNumber", orderNumber))
            .andExpect(status().isNotFound());

        assertThat(orders.findByOrderNumber(orderNumber).orElseThrow().getStatus())
            .isEqualTo(OrderStatus.PENDING_PAYMENT);
    }
}
