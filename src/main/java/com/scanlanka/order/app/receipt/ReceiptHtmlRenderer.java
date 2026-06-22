package com.scanlanka.order.app.receipt;

import com.scanlanka.shared.text.HtmlEscaper;

import org.springframework.stereotype.Component;

import java.util.Locale;

/** Escaped HTML receipt/invoice for PDF rendering (09 FR-18, T-21). No external resources. */
@Component
public class ReceiptHtmlRenderer {

    public String render(ReceiptModel m) {
        String title = m.invoice() ? "Tax Invoice" : "Receipt";
        StringBuilder lines = new StringBuilder();
        for (ReceiptModel.Line line : m.lines()) {
            lines.append("<tr><td>").append(HtmlEscaper.escape(line.sku()))
                .append("</td><td>").append(HtmlEscaper.escape(line.name()))
                .append("</td><td>").append(HtmlEscaper.escape(line.spec()))
                .append("</td><td>").append(line.quantity())
                .append("</td><td>").append(lkr(line.unitPriceCents()))
                .append("</td><td>").append(lkr(line.lineTotalCents()))
                .append("</td></tr>");
        }
        String billing = m.invoice() ? billingBlock(m) : "";
        return """
            <!DOCTYPE html><html><head><meta charset="UTF-8"/>
            <style>
              body { font-family: sans-serif; font-size: 12px; color: #111; }
              h1 { font-size: 18px; margin-bottom: 4px; }
              table { width: 100%%; border-collapse: collapse; margin-top: 12px; }
              th, td { border: 1px solid #ccc; padding: 6px; text-align: left; }
              th { background: #f4f4f4; }
              .totals { margin-top: 12px; width: 280px; margin-left: auto; }
              .totals td { border: none; padding: 2px 0; }
              .muted { color: #555; }
            </style></head><body>
            <h1>Scan Lanka — %s</h1>
            <p class="muted">Order <strong>%s</strong></p>
            <p>%s<br/>%s</p>
            %s
            <p><strong>Fulfilment:</strong> %s · <strong>Payment:</strong> %s (%s)</p>
            %s
            <table><thead><tr><th>SKU</th><th>Item</th><th>Spec</th><th>Qty</th><th>Unit</th><th>Total</th></tr></thead>
            <tbody>%s</tbody></table>
            <table class="totals">
              <tr><td>Subtotal</td><td>%s</td></tr>
              %s
              <tr><td>Tax</td><td>%s</td></tr>
              <tr><td><strong>Paid online</strong></td><td><strong>%s</strong></td></tr>
              %s
            </table>
            </body></html>
            """.formatted(
            title,
            HtmlEscaper.escape(m.orderNumber()),
            HtmlEscaper.escape(m.contactName()),
            HtmlEscaper.escape(m.contactEmail()),
            billing,
            HtmlEscaper.escape(m.fulfilmentType()),
            HtmlEscaper.escape(m.paymentMethod()),
            HtmlEscaper.escape(m.deliveryPayment()),
            shipBlock(m),
            lines,
            lkr(m.subtotalCents()),
            deliveryRow(m),
            lkr(m.taxCents()),
            lkr(m.totalCents()),
            codRow(m));
    }

    private static String billingBlock(ReceiptModel m) {
        return """
            <p><strong>Bill to:</strong> %s<br/>Tax ID: %s<br/>%s, %s, %s %s</p>
            """.formatted(
            HtmlEscaper.escape(nullToDash(m.billName())),
            HtmlEscaper.escape(nullToDash(m.billTaxId())),
            HtmlEscaper.escape(nullToDash(m.billStreet())),
            HtmlEscaper.escape(nullToDash(m.billCity())),
            HtmlEscaper.escape(nullToDash(m.billProvince())),
            HtmlEscaper.escape(nullToDash(m.billPostalCode())));
    }

    private static String shipBlock(ReceiptModel m) {
        if (m.shipStreet() == null || m.shipStreet().isBlank()) {
            return "<p><strong>Delivery:</strong> Pickup</p>";
        }
        return """
            <p><strong>Ship to:</strong> %s, %s, %s %s</p>
            """.formatted(
            HtmlEscaper.escape(m.shipStreet()),
            HtmlEscaper.escape(nullToDash(m.shipCity())),
            HtmlEscaper.escape(nullToDash(m.shipProvince())),
            HtmlEscaper.escape(nullToDash(m.shipPostalCode())));
    }

    private static String deliveryRow(ReceiptModel m) {
        if ("COD".equalsIgnoreCase(m.deliveryPayment())) {
            return "<tr><td>Delivery (pay on delivery)</td><td>" + lkr(m.deliveryCodCents()) + "</td></tr>";
        }
        return "<tr><td>Delivery</td><td>" + lkr(m.deliveryCents()) + "</td></tr>";
    }

    private static String codRow(ReceiptModel m) {
        if ("COD".equalsIgnoreCase(m.deliveryPayment()) && m.deliveryCodCents() > 0) {
            return "<tr><td>Collect on delivery</td><td>" + lkr(m.deliveryCodCents()) + "</td></tr>";
        }
        return "";
    }

    private static String lkr(long cents) {
        return String.format(Locale.US, "Rs %.2f", cents / 100.0);
    }

    private static String nullToDash(String s) {
        return s == null || s.isBlank() ? "—" : s;
    }
}
