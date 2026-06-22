package com.scanlanka.notification.app;

import com.scanlanka.order.app.receipt.ReceiptModel;
import com.scanlanka.shared.text.HtmlEscaper;

import java.util.Locale;

import org.springframework.stereotype.Component;

/** Escaped HTML email bodies (10 FR-NOTIFY-1/2/3/6). */
@Component
public class EmailTemplateRenderer {

    public record RenderedEmail(String subject, String body) {}

    public RenderedEmail orderReceipt(ReceiptModel m, String lookupUrl) {
        String subject = HtmlEscaper.subject("Your Scan Lanka receipt — " + m.orderNumber());
        StringBuilder lines = new StringBuilder();
        for (ReceiptModel.Line line : m.lines()) {
            lines.append("<li>").append(HtmlEscaper.escape(line.name()))
                .append(" (").append(HtmlEscaper.escape(line.sku())).append(") × ")
                .append(line.quantity()).append(" — ")
                .append(lkr(line.lineTotalCents())).append("</li>");
        }
        String body = """
            <p>Hi %s,</p>
            <p>Thank you for your order <strong>%s</strong>.</p>
            <ul>%s</ul>
            <p>Subtotal: %s<br/>%s<br/>Tax: %s<br/><strong>Paid online: %s</strong></p>
            %s
            <p>Download your receipt PDF from your account or via <a href="%s">order lookup</a>.</p>
            <p>— Scan Lanka</p>
            """.formatted(
            HtmlEscaper.escape(m.contactName()),
            HtmlEscaper.escape(m.orderNumber()),
            lines,
            lkr(m.subtotalCents()),
            deliveryLine(m),
            lkr(m.taxCents()),
            lkr(m.totalCents()),
            codNote(m),
            HtmlEscaper.escape(lookupUrl));
        return new RenderedEmail(subject, body);
    }

    public RenderedEmail adminDispatch(ReceiptModel m) {
        String subject = HtmlEscaper.subject("New order " + m.orderNumber());
        StringBuilder lines = new StringBuilder();
        for (ReceiptModel.Line line : m.lines()) {
            lines.append("<tr><td>").append(HtmlEscaper.escape(line.sku()))
                .append("</td><td>").append(HtmlEscaper.escape(line.name()))
                .append("</td><td>").append(HtmlEscaper.escape(line.spec()))
                .append("</td><td>").append(line.quantity())
                .append("</td><td>").append(lkr(line.lineTotalCents()))
                .append("</td></tr>");
        }
        String ship = m.shipStreet() == null || m.shipStreet().isBlank()
            ? "Pickup — " + HtmlEscaper.escape(m.fulfilmentType())
            : HtmlEscaper.escape(m.shipStreet()) + ", " + HtmlEscaper.escape(m.shipCity())
                + " " + HtmlEscaper.escape(m.shipPostalCode());
        String body = """
            <p><strong>New order %s</strong></p>
            <p>Customer: %s (%s)<br/>Fulfilment: %s<br/>Payment: %s / %s<br/>Delivery: %s</p>
            <table border="1" cellpadding="4"><tr><th>SKU</th><th>Item</th><th>Spec</th><th>Qty</th><th>Total</th></tr>
            %s</table>
            <p>Online total: <strong>%s</strong>%s</p>
            """.formatted(
            HtmlEscaper.escape(m.orderNumber()),
            HtmlEscaper.escape(m.contactName()),
            HtmlEscaper.escape(m.contactEmail()),
            HtmlEscaper.escape(m.fulfilmentType()),
            HtmlEscaper.escape(m.paymentMethod()),
            HtmlEscaper.escape(m.deliveryPayment()),
            ship,
            lines,
            lkr(m.totalCents()),
            codNoteHtml(m));
        return new RenderedEmail(subject, body);
    }

    public RenderedEmail emailVerify(String name, String code) {
        return new RenderedEmail(
            HtmlEscaper.subject("Verify your Scan Lanka email"),
            "<p>Hi " + HtmlEscaper.escape(name) + ",</p>"
                + "<p>Your verification code is: <strong>" + HtmlEscaper.escape(code) + "</strong></p>"
                + "<p>This code expires soon. If you did not register, ignore this email.</p>");
    }

    public RenderedEmail passwordReset(String code) {
        return new RenderedEmail(
            HtmlEscaper.subject("Reset your Scan Lanka password"),
            "<p>Your password reset code is: <strong>" + HtmlEscaper.escape(code) + "</strong></p>"
                + "<p>If you did not request a reset, ignore this email.</p>");
    }

    private static String deliveryLine(ReceiptModel m) {
        if ("COD".equalsIgnoreCase(m.deliveryPayment())) {
            return "Delivery (pay on delivery): " + lkr(m.deliveryCodCents());
        }
        return "Delivery: " + lkr(m.deliveryCents());
    }

    private static String codNote(ReceiptModel m) {
        if ("COD".equalsIgnoreCase(m.deliveryPayment()) && m.deliveryCodCents() > 0) {
            return "<p>Collect on delivery: " + lkr(m.deliveryCodCents()) + "</p>";
        }
        return "";
    }

    private static String codNoteHtml(ReceiptModel m) {
        if ("COD".equalsIgnoreCase(m.deliveryPayment()) && m.deliveryCodCents() > 0) {
            return "<br/>Collect on delivery: " + lkr(m.deliveryCodCents());
        }
        return "";
    }

    private static String lkr(long cents) {
        return String.format(Locale.US, "Rs %.2f", cents / 100.0);
    }
}
