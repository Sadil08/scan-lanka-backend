package com.scanlanka.order.app.receipt;

import java.util.List;

/** Server-built receipt/invoice model (09 FR-18). All fields escaped at render time. */
public record ReceiptModel(
    String orderNumber,
    String contactName,
    String contactEmail,
    String fulfilmentType,
    String deliveryPayment,
    String paymentMethod,
    String shipStreet,
    String shipCity,
    String shipProvince,
    String shipPostalCode,
    String billName,
    String billTaxId,
    String billStreet,
    String billCity,
    String billProvince,
    String billPostalCode,
    long subtotalCents,
    long deliveryCents,
    long taxCents,
    long totalCents,
    long deliveryCodCents,
    List<Line> lines,
    boolean invoice
) {
    public record Line(String sku, String name, String spec, int quantity, long unitPriceCents, long lineTotalCents) {}
}
