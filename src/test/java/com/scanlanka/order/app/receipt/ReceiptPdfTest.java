package com.scanlanka.order.app.receipt;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ReceiptPdfTest {

    private final ReceiptPdfRenderer renderer = new ReceiptPdfRenderer(new ReceiptHtmlRenderer());

    @Test
    void rendersValidPdfBytes() {
        ReceiptModel model = new ReceiptModel(
            "SL-ABC123", "Customer", "c@x.lk", "PICKUP_SHOP", "PREPAID", "PAYHERE",
            null, null, null, null,
            "Biz Ltd", "VAT123", "Bill St", "City", "WP", "00200",
            5000, 0, 250, 5250, 0,
            List.of(new ReceiptModel.Line("SKU-1", "Product", "Large", 2, 2500, 5000)),
            true);
        byte[] pdf = renderer.render(model);
        assertThat(pdf.length).isGreaterThan(100);
        assertThat(new String(pdf, 0, 4)).isEqualTo("%PDF");
    }
}
