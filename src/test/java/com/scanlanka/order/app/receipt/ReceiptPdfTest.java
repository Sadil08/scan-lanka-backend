package com.scanlanka.order.app.receipt;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ReceiptPdfTest {

    private final ReceiptPdfRenderer renderer = new ReceiptPdfRenderer(new ReceiptHtmlRenderer());

    @Test
    void rendersValidPdfBytes() {
        // Every order is delivered - pickup was removed (17) - so a real ship address, not null.
        ReceiptModel model = new ReceiptModel(
            "SL-ABC123", "Customer", "c@x.lk", "0771234567", "DELIVERY", "COMPANY_LORRY", "PREPAID", "PAYHERE",
            "1 Main St", "Colombo", "Western", "00200",
            "Biz Ltd", "VAT123", "Bill St", "City", "WP", "00200",
            5000, 800, 250, 0, 6050, 0, 0,
            List.of(new ReceiptModel.Line("SKU-1", "Product", "Large", 2, 2500, 5000)),
            true);
        byte[] pdf = renderer.render(model);
        assertThat(pdf.length).isGreaterThan(100);
        assertThat(new String(pdf, 0, 4)).isEqualTo("%PDF");
    }

    @Test
    void courierOrderShowsEstimateAndApproxDoorTotal() {
        ReceiptModel model = new ReceiptModel(
            "SL-CUR001", "Customer", "c@x.lk", "0771234567", "DELIVERY", "COURIER", "COD", "COD",
            "1 Main St", "Kandy", "Central", "20000",
            null, null, null, null, null, null,
            5000, 0, 0, 0, 0, 0, 1500,
            List.of(new ReceiptModel.Line("SKU-1", "Product", "-", 1, 5000, 5000)),
            false);
        String html = new ReceiptHtmlRenderer().render(model);
        assertThat(html).contains("Courier fee").contains("Rs 15.00");
        assertThat(html).contains("Approx. total on delivery").contains("Rs 65.00"); // 5000+0+1500 cents
    }

    @Test
    void lorryCodOrderShowsFeeAndCashOnDeliveryTotal() {
        ReceiptModel model = new ReceiptModel(
            "SL-LCD001", "Customer", "c@x.lk", "0771234567", "DELIVERY", "COMPANY_LORRY", "COD", "COD",
            "1 Main St", "Colombo", "Western", "00100",
            null, null, null, null, null, null,
            5000, 800, 0, 0, 0, 5800, 0,
            List.of(new ReceiptModel.Line("SKU-1", "Product", "-", 1, 5000, 5000)),
            false);
        String html = new ReceiptHtmlRenderer().render(model);
        assertThat(html).contains("Lorry delivery").contains("Rs 8.00");
        assertThat(html).contains("Cash on delivery").contains("Rs 58.00");
    }

    @Test
    void cardOrderShowsPayhereFeeRow_bankOrderDoesNot() {
        ReceiptModel card = new ReceiptModel(
            "SL-CRD001", "Customer", "c@x.lk", "0771234567", "DELIVERY", "COMPANY_LORRY", "PREPAID", "PAYHERE",
            "1 Main St", "Colombo", "Western", "00100",
            null, null, null, null, null, null,
            5000, 800, 0, 25, 5825, 0, 0,
            List.of(new ReceiptModel.Line("SKU-1", "Product", "-", 1, 5000, 5000)),
            false);
        String html = new ReceiptHtmlRenderer().render(card);
        assertThat(html).contains("PayHere card fee").contains("Rs 0.25");

        ReceiptModel bank = new ReceiptModel(
            "SL-BNK001", "Customer", "c@x.lk", "0771234567", "DELIVERY", "COMPANY_LORRY", "PREPAID", "BANK",
            "1 Main St", "Colombo", "Western", "00100",
            null, null, null, null, null, null,
            5000, 800, 0, 0, 5800, 0, 0,
            List.of(new ReceiptModel.Line("SKU-1", "Product", "-", 1, 5000, 5000)),
            false);
        assertThat(new ReceiptHtmlRenderer().render(bank)).doesNotContain("PayHere card fee");
    }
}
