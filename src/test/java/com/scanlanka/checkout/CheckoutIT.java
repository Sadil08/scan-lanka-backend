package com.scanlanka.checkout;

import com.scanlanka.AbstractIntegrationTest;
import com.scanlanka.catalog.app.ProductService;
import com.scanlanka.catalog.domain.Product;
import com.scanlanka.catalog.infra.ProductRepository;
import com.scanlanka.catalog.web.dto.ProductRequests.CreateProductRequest;
import com.scanlanka.checkout.domain.BoardSizeTier;
import com.scanlanka.checkout.domain.CourierZone;
import com.scanlanka.checkout.domain.LorryZone;
import com.scanlanka.checkout.domain.PostalZone;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scanlanka.checkout.infra.PostalZoneRepository;
import com.scanlanka.order.domain.DeliveryPayment;
import com.scanlanka.order.domain.Order;
import com.scanlanka.order.domain.OrderStatus;
import com.scanlanka.order.infra.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Checkout two-rail quote + place, end-to-end on real Postgres (05/17). Server computes all totals (SEC-PAY). */
class CheckoutIT extends AbstractIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ProductService productService;
    @Autowired ProductRepository products;
    @Autowired PostalZoneRepository postalZones;
    @Autowired OrderRepository orders;
    @Autowired ObjectMapper objectMapper;

    @BeforeEach
    void seedPostals() {
        if (!postalZones.existsById("00100")) {          // Colombo — in-house-lorry-only zone
            postalZones.save(new PostalZone("00100", LorryZone.COLOMBO, CourierZone.CITY_LIMITS,
                "Colombo", "Western Province"));
        }
        if (!postalZones.existsById("20000")) {          // Kandy — OUTER zone (courier offered here)
            postalZones.save(new PostalZone("20000", LorryZone.OUTER, CourierZone.OUTSTATION,
                "Kandy", "Central Province"));
        }
    }

    /** A single-priced product with a Colombo lorry charge and a courier size tier. */
    private Long seedProduct(long priceCents, Long lorryColomboCents, BoardSizeTier boardSizeTier) {
        Long id = productService.create(new CreateProductRequest(
            null, "Board " + System.nanoTime(), null, null, null, "Boards", null, 100, priceCents,
            List.of(), List.of()));
        Product p = products.findById(id).orElseThrow();
        p.setLorryColomboCents(lorryColomboCents);
        p.setBoardSizeTier(boardSizeTier);
        products.save(p);
        return id;
    }

    /** A small gated product: no priced Colombo cell, a Rs-threshold gate for the Colombo zone. */
    private Long seedGatedProduct(long priceCents, long colomboGateCents) {
        Long id = productService.create(new CreateProductRequest(
            null, "Small " + System.nanoTime(), null, null, null, "Boards", null, 100, priceCents,
            List.of(), List.of()));
        Product p = products.findById(id).orElseThrow();
        p.setLorryColomboGateCents(colomboGateCents);
        p.setBoardSizeTier(BoardSizeTier.UNDER_2FT);
        products.save(p);
        return id;
    }

    @Test
    void lorryQuoteOverMinBillChargesProductPlusLorryOnline() throws Exception {
        Long id = seedProduct(700000, 100000L, BoardSizeTier.BETWEEN_2FT_6FT); // Rs 7,000 > gate; lorry Rs 1,000
        mvc.perform(post("/api/checkout/quote").contentType(MediaType.APPLICATION_JSON)
                .content("{\"items\":[{\"productId\":" + id + ",\"quantity\":1}],"
                    + "\"deliveryMethod\":\"COMPANY_LORRY\",\"postalCode\":\"00100\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.available").value(true))
            .andExpect(jsonPath("$.subtotalCents").value(700000))
            .andExpect(jsonPath("$.deliveryCents").value(100000))
            .andExpect(jsonPath("$.onlineTotalCents").value(800000));   // product + lorry (0% tax)
    }

    @Test
    void lorryUnavailableUnderGateThreshold() throws Exception {
        // Per-cell gate (owner 2026-07-03): Rs 3,000 threshold, subtotal Rs 2.50 ≤ gate → no lorry.
        Long id = seedGatedProduct(250, 300000L);
        mvc.perform(post("/api/checkout/quote").contentType(MediaType.APPLICATION_JSON)
                .content("{\"items\":[{\"productId\":" + id + ",\"quantity\":1}],"
                    + "\"deliveryMethod\":\"COMPANY_LORRY\",\"postalCode\":\"00100\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.available").value(false))
            .andExpect(jsonPath("$.reason").value("MIN_BILL_NOT_MET"));
    }

    @Test
    void courierAvailableInColomboCityLimits() throws Exception {
        // owner 2026-07-03: courier is offered everywhere; Colombo city-limits uses the Domex CITY_LIMITS rate.
        Long id = seedProduct(250, 100000L, BoardSizeTier.BETWEEN_2FT_6FT);
        mvc.perform(post("/api/checkout/quote").contentType(MediaType.APPLICATION_JSON)
                .content("{\"items\":[{\"productId\":" + id + ",\"quantity\":1}],"
                    + "\"deliveryMethod\":\"COURIER\",\"postalCode\":\"00100\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.available").value(true))
            .andExpect(jsonPath("$.courierEstimateCents").value(100000)); // CITY_LIMITS 2–6 ft = Rs 1,000
    }

    @Test
    void largeBoardBlockedFromCourierToOuterButOkToCity() throws Exception {
        // 6×4/8×4/6×3 can't be couriered to outer Domex areas (owner 2026-07-03) — contact us there.
        Long id = seedProduct(250, 100000L, BoardSizeTier.BETWEEN_2FT_6FT);
        Product p = products.findById(id).orElseThrow();
        p.setCourierOuterBlocked(true);
        products.save(p);

        mvc.perform(post("/api/checkout/quote").contentType(MediaType.APPLICATION_JSON)
                .content("{\"items\":[{\"productId\":" + id + ",\"quantity\":1}],"
                    + "\"deliveryMethod\":\"COURIER\",\"postalCode\":\"20000\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.available").value(false))
            .andExpect(jsonPath("$.reason").value("OVERSIZE_OUTER"));

        mvc.perform(post("/api/checkout/quote").contentType(MediaType.APPLICATION_JSON)
                .content("{\"items\":[{\"productId\":" + id + ",\"quantity\":1}],"
                    + "\"deliveryMethod\":\"COURIER\",\"postalCode\":\"00100\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.available").value(true))     // same board couriers fine to city-limits
            .andExpect(jsonPath("$.courierEstimateCents").value(100000));
    }

    @Test
    void courierQuoteInOuterZoneIsFullCodWithSizeTierEstimate() throws Exception {
        Long id = seedProduct(250, 100000L, BoardSizeTier.BETWEEN_2FT_6FT);
        mvc.perform(post("/api/checkout/quote").contentType(MediaType.APPLICATION_JSON)
                .content("{\"items\":[{\"productId\":" + id + ",\"quantity\":1}],"
                    + "\"deliveryMethod\":\"COURIER\",\"postalCode\":\"20000\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.available").value(true))
            .andExpect(jsonPath("$.onlineTotalCents").value(0))
            .andExpect(jsonPath("$.courierEstimateCents").value(150000))  // OUTSTATION, 2–6 ft = Rs 1,500
            .andExpect(jsonPath("$.approxTotalCents").value(150250));
    }

    @Test
    void unknownPostcodeMakesRailUnavailable() throws Exception {
        Long id = seedProduct(700000, 100000L, BoardSizeTier.BETWEEN_2FT_6FT);
        mvc.perform(post("/api/checkout/quote").contentType(MediaType.APPLICATION_JSON)
                .content("{\"items\":[{\"productId\":" + id + ",\"quantity\":1}],"
                    + "\"deliveryMethod\":\"COMPANY_LORRY\",\"postalCode\":\"99999\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.available").value(false))
            .andExpect(jsonPath("$.reason").value("NOT_SERVICEABLE_POSTAL"));
    }

    @Test
    void placeCreatesAnOrderWithSignedNumber() throws Exception {
        Long id = seedProduct(700000, 100000L, BoardSizeTier.BETWEEN_2FT_6FT);
        mvc.perform(post("/api/checkout").contentType(MediaType.APPLICATION_JSON)
                .content("{\"items\":[{\"productId\":" + id + ",\"quantity\":1}],"
                    + "\"deliveryMethod\":\"COMPANY_LORRY\","
                    + "\"ship\":{\"street\":\"1 Main\",\"city\":\"Colombo\",\"province\":\"Western\",\"postalCode\":\"00100\"},"
                    + "\"contactName\":\"Mark\",\"contactPhone\":\"+94770000000\",\"contactEmail\":\"mark@x.lk\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.orderNumber").exists())
            .andExpect(jsonPath("$.onlineTotalCents").value(800000));
    }

    @Test
    void courierOrderIsConfirmedFullCodAndDecrementsStock() throws Exception {
        Long id = seedProduct(250, null, BoardSizeTier.UNDER_2FT);
        int stockBefore = products.findById(id).orElseThrow().getStockQty();

        MvcResult res = mvc.perform(post("/api/checkout").contentType(MediaType.APPLICATION_JSON)
                .content("{\"items\":[{\"productId\":" + id + ",\"quantity\":1}],"
                    + "\"deliveryMethod\":\"COURIER\","
                    + "\"ship\":{\"street\":\"1 Main\",\"city\":\"Kandy\",\"province\":\"Central\",\"postalCode\":\"20000\"},"
                    + "\"contactName\":\"Mark\",\"contactPhone\":\"+94770000000\",\"contactEmail\":\"mark@x.lk\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.onlineTotalCents").value(0))   // full COD — nothing online
            .andReturn();
        String orderNumber = objectMapper.readTree(res.getResponse().getContentAsString()).get("orderNumber").asText();

        Order o = orders.findByOrderNumber(orderNumber).orElseThrow();
        assertThat(o.getStatus()).isEqualTo(OrderStatus.CONFIRMED);      // confirmed on placement, not pending
        assertThat(o.getDeliveryPayment()).isEqualTo(DeliveryPayment.COD);
        assertThat(o.getTotalCents()).isZero();
        assertThat(o.getCourierEstimateCents()).isEqualTo(100000);       // Rs 1,000 under 2 ft OUTSTATION
        assertThat(products.findById(id).orElseThrow().getStockQty()).isEqualTo(stockBefore - 1); // decremented now

        // FR-PAY-15: a courier (full-COD) order cannot be paid online
        mvc.perform(post("/api/payments/payhere/initiate").contentType(MediaType.APPLICATION_JSON)
                .content("{\"orderNumber\":\"" + orderNumber + "\"}"))
            .andExpect(status().isConflict());
    }

    @Test
    void lorryCodOrderIsConfirmedAndCollectsDoorTotalInCash() throws Exception {
        // owner 2026-07-03: lorry cash-on-delivery — nothing online, driver collects product + lorry charge.
        Long id = seedProduct(700000, 100000L, BoardSizeTier.BETWEEN_2FT_6FT); // Rs 7,000 + Rs 1,000 lorry
        int stockBefore = products.findById(id).orElseThrow().getStockQty();

        MvcResult res = mvc.perform(post("/api/checkout").contentType(MediaType.APPLICATION_JSON)
                .content("{\"items\":[{\"productId\":" + id + ",\"quantity\":1}],"
                    + "\"deliveryMethod\":\"COMPANY_LORRY\",\"paymentChoice\":\"COD\","
                    + "\"ship\":{\"street\":\"1 Main\",\"city\":\"Colombo\",\"province\":\"Western\",\"postalCode\":\"00100\"},"
                    + "\"contactName\":\"Mark\",\"contactPhone\":\"+94770000000\",\"contactEmail\":\"mark@x.lk\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.onlineTotalCents").value(0))   // COD — nothing charged online
            .andReturn();
        String orderNumber = objectMapper.readTree(res.getResponse().getContentAsString()).get("orderNumber").asText();

        Order o = orders.findByOrderNumber(orderNumber).orElseThrow();
        assertThat(o.getStatus()).isEqualTo(OrderStatus.CONFIRMED);      // confirmed on placement like courier
        assertThat(o.getDeliveryPayment()).isEqualTo(DeliveryPayment.COD);
        assertThat(o.getTotalCents()).isZero();                          // nothing online
        assertThat(o.getDeliveryCodCents()).isEqualTo(800000);          // door total: Rs 7,000 + Rs 1,000
        assertThat(products.findById(id).orElseThrow().getStockQty()).isEqualTo(stockBefore - 1);
    }

    @Test
    void courierOrderRejectsExplicitOnlinePayment() throws Exception {
        Long id = seedProduct(250, null, BoardSizeTier.UNDER_2FT);
        mvc.perform(post("/api/checkout").contentType(MediaType.APPLICATION_JSON)
                .content("{\"items\":[{\"productId\":" + id + ",\"quantity\":1}],"
                    + "\"deliveryMethod\":\"COURIER\",\"paymentChoice\":\"ONLINE\","
                    + "\"ship\":{\"street\":\"1 Main\",\"city\":\"Kandy\",\"province\":\"Central\",\"postalCode\":\"20000\"},"
                    + "\"contactName\":\"Mark\",\"contactPhone\":\"+94770000000\",\"contactEmail\":\"mark@x.lk\"}"))
            .andExpect(status().isUnprocessableEntity());
    }
}
