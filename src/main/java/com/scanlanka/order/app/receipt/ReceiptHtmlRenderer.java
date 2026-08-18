package com.scanlanka.order.app.receipt;

import com.scanlanka.shared.branding.BrandAssets;
import com.scanlanka.shared.text.HtmlEscaper;

import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * Escaped HTML receipt/invoice for PDF rendering (09 FR-18, T-21), styled as a letterhead using the
 * shared {@link BrandAssets} (logo + brand blue) so PDFs match the storefront look. No external
 * resources - the logo is a base64 data URI, safe for openhtmltopdf's no-fetch policy.
 */
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
              body { font-family: sans-serif; font-size: 12px; color: %s; margin: 0; padding: 24px; }
              .sheet { max-width: 720px; margin: 0 auto; }
              .letterhead { display: flex; justify-content: space-between; align-items: flex-start;
                border-bottom: 3px solid %s; padding-bottom: 12px; margin-bottom: 16px; }
              .letterhead img { height: 42px; }
              .company { text-align: right; font-size: 11px; color: %s; line-height: 1.5; }
              .company strong { color: %s; font-size: 13px; }
              h1 { font-size: 19px; margin: 0 0 4px; color: %s; }
              table { width: 100%%; border-collapse: collapse; margin-top: 12px; }
              th, td { border: 1px solid #d7e3ec; padding: 7px; text-align: left; }
              th { background: %s; color: %s; font-weight: 600; }
              .totals { margin-top: 12px; width: 300px; margin-left: auto; }
              .totals td { border: none; padding: 3px 0; }
              .totals tr:last-child td { border-top: 2px solid %s; padding-top: 6px; font-size: 13px; }
              .muted { color: %s; }
              .footer { margin-top: 28px; border-top: 1px solid %s; padding-top: 10px;
                font-size: 10.5px; color: %s; text-align: center; }
            </style></head><body>
            <div class="sheet">
              <div class="letterhead">
                <img src="%s" alt="Scan Lanka"/>
                <div class="company">
                  <strong>%s</strong><br/>%s<br/>%s · %s
                </div>
              </div>
              <h1>%s</h1>
              <p class="muted">Order <strong>%s</strong></p>
              <p>%s<br/>%s<br/>Phone: %s</p>
              %s
              <p><strong>Fulfilment:</strong> %s · <strong>Payment:</strong> %s (%s)</p>
              %s
              <table><thead><tr><th>SKU</th><th>Item</th><th>Handling</th><th>Qty</th><th>Unit</th><th>Total</th></tr></thead>
              <tbody>%s</tbody></table>
              <table class="totals">
                <tr><td>Subtotal</td><td>%s</td></tr>
                %s
                <tr><td>Tax</td><td>%s</td></tr>
                %s
                <tr><td><strong>Paid online</strong></td><td><strong>%s</strong></td></tr>
                %s
              </table>
              <div class="footer">
                Thank you for choosing Scan Lanka.<br/>
                %s &#183; %s &#183; %s
              </div>
            </div>
            </body></html>
            """.formatted(
            BrandAssets.INK, BrandAssets.PRIMARY, BrandAssets.MUTED, BrandAssets.PRIMARY, BrandAssets.PRIMARY_DARK,
            BrandAssets.PRIMARY_LIGHT, BrandAssets.PRIMARY_DARK, BrandAssets.PRIMARY, BrandAssets.MUTED,
            BrandAssets.PRIMARY_LIGHT, BrandAssets.MUTED,
            BrandAssets.LOGO_DATA_URI,
            HtmlEscaper.escape(BrandAssets.COMPANY_NAME),
            HtmlEscaper.escape(BrandAssets.COMPANY_ADDRESS),
            HtmlEscaper.escape(BrandAssets.COMPANY_PHONE),
            HtmlEscaper.escape(BrandAssets.COMPANY_EMAIL),
            title,
            HtmlEscaper.escape(m.orderNumber()),
            HtmlEscaper.escape(m.contactName()),
            HtmlEscaper.escape(m.contactEmail()),
            HtmlEscaper.escape(nullToDash(m.contactPhone())),
            billing,
            HtmlEscaper.escape(m.fulfilmentType()),
            HtmlEscaper.escape(m.paymentMethod()),
            HtmlEscaper.escape(m.deliveryPayment()),
            shipBlock(m),
            lines,
            lkr(m.subtotalCents()),
            deliveryRow(m),
            lkr(m.taxCents()),
            payhereFeeRow(m),
            lkr(m.totalCents()),
            codRow(m),
            BrandAssets.COMPANY_ADDRESS, BrandAssets.COMPANY_PHONE, BrandAssets.COMPANY_EMAIL);
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
        // Every order is delivered - pickup was removed (17, owner 2026-06-27); a blank ship address
        // should never happen post-removal, but render something sane rather than implying pickup exists.
        if (m.shipStreet() == null || m.shipStreet().isBlank()) {
            return "<p><strong>Delivery:</strong> address on file</p>";
        }
        return """
            <p><strong>Ship to:</strong> %s, %s, %s %s</p>
            """.formatted(
            HtmlEscaper.escape(m.shipStreet()),
            HtmlEscaper.escape(nullToDash(m.shipCity())),
            HtmlEscaper.escape(nullToDash(m.shipProvince())),
            HtmlEscaper.escape(nullToDash(m.shipPostalCode())));
    }

    /**
     * The delivery/courier FEE itself (17) - distinct from the "amount to collect" row below. Always
     * shown regardless of payment choice: a lorry order's delivery fee is the same whether paid online
     * or COD; a courier order's fee is Domex's estimate (never charged online, always COD).
     */
    private static String deliveryRow(ReceiptModel m) {
        if ("COURIER".equalsIgnoreCase(m.deliveryMethod())) {
            return "<tr><td>Courier fee (approx., pay on delivery)</td><td>"
                + lkr(m.courierEstimateCents()) + "</td></tr>";
        }
        return "<tr><td>Lorry delivery</td><td>" + lkr(m.deliveryCents()) + "</td></tr>";
    }

    /** PayHere's card surcharge — shown only when actually charged (CARD orders with a nonzero rate). */
    private static String payhereFeeRow(ReceiptModel m) {
        if (m.payhereFeeCents() <= 0) return "";
        return "<tr><td>PayHere card fee</td><td>" + lkr(m.payhereFeeCents()) + "</td></tr>";
    }

    /** The amount actually collected at the door, when the order isn't fully prepaid online. */
    private static String codRow(ReceiptModel m) {
        if (!"COD".equalsIgnoreCase(m.deliveryPayment())) {
            return "";
        }
        if ("COURIER".equalsIgnoreCase(m.deliveryMethod())) {
            long approxDoor = m.subtotalCents() + m.taxCents() + m.courierEstimateCents();
            return "<tr><td>Approx. total on delivery (to the courier)</td><td>" + lkr(approxDoor) + "</td></tr>";
        }
        return "<tr><td>Cash on delivery</td><td>" + lkr(m.deliveryCodCents()) + "</td></tr>";
    }

    private static String lkr(long cents) {
        return String.format(Locale.US, "Rs %.2f", cents / 100.0);
    }

    private static String nullToDash(String s) {
        return s == null || s.isBlank() ? "—" : s;
    }
}
