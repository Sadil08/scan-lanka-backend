package com.scanlanka.order.app.receipt;

import java.util.List;

/** Server-built receipt/invoice model (09 FR-18, 17). All fields escaped at render time. */
public record ReceiptModel(
    String orderNumber,
    String contactName,
    String contactEmail,
    String contactPhone,
    String fulfilmentType,
    String deliveryMethod,       // "COMPANY_LORRY" | "COURIER" (17) - which rail, not who pays
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
    long payhereFeeCents,      // PayHere card surcharge, 0 unless paid by CARD (admin-tunable)
    long totalCents,
    long deliveryCodCents,
    long courierEstimateCents,  // Domex estimate, display-only (17 FR-DELIV-5) - 0 for lorry orders
    List<Line> lines,
    boolean invoice
) {
    public record Line(String sku, String name, String spec, int quantity, long unitPriceCents, long lineTotalCents) {}
}
