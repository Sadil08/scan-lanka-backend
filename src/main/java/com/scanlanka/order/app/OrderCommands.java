package com.scanlanka.order.app;

import com.scanlanka.order.domain.DeliveryPayment;
import com.scanlanka.order.domain.FulfilmentType;

import java.util.List;

/** Inputs for creating an order draft (built by checkout from the validated cart). */
public final class OrderCommands {

    private OrderCommands() {}

    public record Address(String street, String city, String province, String postalCode) {}

    public record Billing(String name, String taxId, String street, String city, String province, String postalCode) {}

    public record LineSnapshot(Long productId, Long variantId, String sku, String name, String handlingClass,
                               long unitPriceCents, int quantity, long lineTotalCents) {}

    public record CreateOrderCommand(
        Long customerId, String guestEmail,
        String contactName, String contactPhone, String contactEmail,
        FulfilmentType fulfilmentType,
        Address ship, Billing billing,
        List<LineSnapshot> lines,
        long subtotalCents, long discountCents, long deliveryCents, long taxCents, long totalCents,
        DeliveryPayment deliveryPayment, long deliveryCodCents,
        String deliveryMethod, long courierEstimateCents, boolean deliveryArranged,
        String paymentMethod) {}
}
