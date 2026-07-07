package com.scanlanka.order.web.dto;

import java.time.Instant;
import java.util.List;

public final class OrderResponses {

    private OrderResponses() {}

    public record OrderSummaryView(
        String orderNumber, String status, long totalCents, long refundTotalCents, Instant createdAt) {}

    public record OrderLineView(
        String name, String sku, int quantity, long unitPriceCents, long lineTotalCents) {}

    public record StatusEventView(String fromStatus, String toStatus, Instant at) {}

    public record OrderDetailView(
        String orderNumber, String status, long subtotalCents, long deliveryCents, long taxCents,
        long totalCents, long refundTotalCents, long deliveryCodCents, String fulfilmentType, String deliveryPayment,
        String deliveryMethod, long courierEstimateCents,
        String carrier, String trackingRef,
        String shipStreet, String shipCity, String shipProvince, String shipPostalCode,
        List<OrderLineView> lines, List<StatusEventView> timeline) {}
}
