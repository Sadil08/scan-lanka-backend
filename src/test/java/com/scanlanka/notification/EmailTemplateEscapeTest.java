package com.scanlanka.notification;

import com.scanlanka.notification.app.EmailTemplateRenderer;
import com.scanlanka.order.app.receipt.ReceiptModel;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EmailTemplateEscapeTest {

    private final EmailTemplateRenderer templates =
        new EmailTemplateRenderer("https://scan-lanka-frontend-gold.vercel.app");

    @Test
    void scriptTagsAreEscapedInReceipt() {
        ReceiptModel model = sample("<script>alert(1)</script>", "Evil\r\nHeader");
        var email = templates.orderReceipt(model, "http://localhost/lookup");
        assertThat(email.body()).doesNotContain("<script>");
        assertThat(email.body()).contains("&lt;script&gt;");
        assertThat(email.subject()).doesNotContain("\r").doesNotContain("\n");
        assertThat(email.body()).contains("https://scan-lanka-frontend-gold.vercel.app/logo.png");
    }

    @Test
    void dispatchEscapesCustomerFields() {
        ReceiptModel model = sample("<img onerror=1>", "Bad\nSubject");
        var email = templates.adminDispatch(model);
        assertThat(email.body()).doesNotContain("<img onerror");
        assertThat(email.body()).contains("&lt;img onerror=1&gt;");
    }

    private static ReceiptModel sample(String name, String subjectHint) {
        return new ReceiptModel(
            "SL-TEST", name, "a@x.lk", "DELIVERY", "COMPANY_LORRY", "PREPAID", "PAYHERE",
            "1 Main St", "Colombo", "WP", "00100",
            null, null, null, null, null, null,
            1000, 200, 50, 1250, 0, 0,
            List.of(new ReceiptModel.Line("SKU1", "Item", "Spec", 1, 1000, 1000)),
            false);
    }
}
