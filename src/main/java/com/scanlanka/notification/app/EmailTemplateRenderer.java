package com.scanlanka.notification.app;

import com.scanlanka.order.app.receipt.ReceiptModel;
import com.scanlanka.shared.branding.BrandAssets;
import com.scanlanka.shared.text.HtmlEscaper;

import java.util.Locale;

import org.springframework.stereotype.Component;

/**
 * Escaped HTML email bodies (10 FR-NOTIFY-1/2/3/6), wrapped in a shared branded letterhead
 * ({@link #envelope}) - logo + brand blue - so every transactional email matches the storefront and
 * the receipt PDF ({@link com.scanlanka.order.app.receipt.ReceiptHtmlRenderer}).
 */
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
            <p>Download your receipt PDF from your account or via <a href="%s" style="color:%s;">order lookup</a>.</p>
            """.formatted(
            HtmlEscaper.escape(m.contactName()),
            HtmlEscaper.escape(m.orderNumber()),
            lines,
            lkr(m.subtotalCents()),
            deliveryLine(m),
            lkr(m.taxCents()),
            lkr(m.totalCents()),
            codNote(m),
            HtmlEscaper.escape(lookupUrl),
            BrandAssets.PRIMARY);
        return new RenderedEmail(subject, envelope("Order receipt", body));
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
        // Every order is delivered - pickup was removed (17, owner 2026-06-27); a blank ship address
        // should never happen post-removal, but render something sane rather than implying pickup exists.
        String ship = m.shipStreet() == null || m.shipStreet().isBlank()
            ? "No address on file"
            : HtmlEscaper.escape(m.shipStreet()) + ", " + HtmlEscaper.escape(m.shipCity())
                + " " + HtmlEscaper.escape(m.shipPostalCode());
        String body = """
            <p><strong>New order %s</strong></p>
            <p>Customer: %s (%s)<br/>Fulfilment: %s<br/>Payment: %s / %s<br/>Delivery: %s</p>
            <table border="1" cellpadding="6" style="border-collapse:collapse;width:100%%;">
            <tr style="background:%s;color:%s;"><th>SKU</th><th>Item</th><th>Handling</th><th>Qty</th><th>Total</th></tr>
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
            BrandAssets.PRIMARY_LIGHT, BrandAssets.PRIMARY_DARK,
            lines,
            lkr(m.totalCents()),
            codNoteHtml(m));
        return new RenderedEmail(subject, envelope("New order notification", body));
    }

    public RenderedEmail emailVerify(String name, String code) {
        // The code sits in its own <span class="code"> (no inline style) so no digit ever appears
        // between "code is:" and the code itself - callers/tools that grep the raw body for the first
        // run of digits after that phrase (e.g. AuthFlowIT) must find the real code, not a CSS value.
        String body = "<p>Hi " + HtmlEscaper.escape(name) + ",</p>"
            + "<p>Your verification code is: <span class=\"code\">" + HtmlEscaper.escape(code) + "</span></p>"
            + "<p>This code expires soon. If you did not register, ignore this email.</p>";
        return new RenderedEmail(
            HtmlEscaper.subject("Verify your Scan Lanka email"),
            envelope("Verify your email", body));
    }

    public RenderedEmail passwordReset(String code) {
        String body = "<p>Your password reset code is: <span class=\"code\">" + HtmlEscaper.escape(code) + "</span></p>"
            + "<p>If you did not request a reset, ignore this email.</p>";
        return new RenderedEmail(
            HtmlEscaper.subject("Reset your Scan Lanka password"),
            envelope("Password reset", body));
    }

    /**
     * Shared branded letterhead for every email: logo + company name in the header, a blue accent
     * border, the caller's content, and a contact-details footer - same look as the receipt PDF.
     */
    private static String envelope(String heading, String innerHtml) {
        return """
            <!DOCTYPE html><html><head><meta charset="UTF-8"/>
            <style>.code { font-size: 18px; letter-spacing: 2px; color: %s; font-weight: bold; }</style>
            </head>
            <body style="margin:0;padding:0;background:%s;font-family:sans-serif;">
              <div style="max-width:560px;margin:0 auto;padding:24px 0;">
                <div style="background:#ffffff;border:1px solid %s;border-radius:8px;overflow:hidden;">
                  <div style="display:flex;align-items:center;gap:12px;padding:20px 24px;border-bottom:3px solid %s;">
                    <img src="%s" alt="Scan Lanka" style="height:36px;"/>
                    <strong style="color:%s;font-size:15px;">%s</strong>
                  </div>
                  <div style="padding:20px 24px 4px;font-size:13px;color:%s;line-height:1.6;">
                    <h2 style="margin:0 0 12px;font-size:15px;color:%s;">%s</h2>
                    %s
                  </div>
                  <div style="padding:14px 24px;border-top:1px solid %s;font-size:11px;color:%s;text-align:center;">
                    %s &#183; %s &#183; %s
                  </div>
                </div>
              </div>
            </body></html>
            """.formatted(
            BrandAssets.PRIMARY_DARK,
            BrandAssets.PRIMARY_LIGHT, BrandAssets.PRIMARY_LIGHT, BrandAssets.PRIMARY,
            BrandAssets.LOGO_CID_URI, BrandAssets.PRIMARY_DARK, HtmlEscaper.escape(BrandAssets.COMPANY_NAME),
            BrandAssets.INK, BrandAssets.PRIMARY_DARK, HtmlEscaper.escape(heading),
            innerHtml,
            BrandAssets.PRIMARY_LIGHT, BrandAssets.MUTED,
            BrandAssets.COMPANY_ADDRESS, BrandAssets.COMPANY_PHONE, BrandAssets.COMPANY_EMAIL);
    }

    /**
     * The delivery/courier FEE itself (17) - distinct from the "amount to collect" note below. Always
     * shown regardless of payment choice: a lorry order's delivery fee is the same whether paid online
     * or COD; a courier order's fee is Domex's estimate (never charged online, always COD).
     */
    private static String deliveryLine(ReceiptModel m) {
        if ("COURIER".equalsIgnoreCase(m.deliveryMethod())) {
            return "Courier fee (approx., pay on delivery): " + lkr(m.courierEstimateCents());
        }
        return "Lorry delivery: " + lkr(m.deliveryCents());
    }

    private static String codNote(ReceiptModel m) {
        if (!"COD".equalsIgnoreCase(m.deliveryPayment())) return "";
        if ("COURIER".equalsIgnoreCase(m.deliveryMethod())) {
            long approxDoor = m.subtotalCents() + m.taxCents() + m.courierEstimateCents();
            return "<p>Approx. total on delivery (to the courier): " + lkr(approxDoor) + "</p>";
        }
        return "<p>Cash on delivery: " + lkr(m.deliveryCodCents()) + "</p>";
    }

    private static String codNoteHtml(ReceiptModel m) {
        if (!"COD".equalsIgnoreCase(m.deliveryPayment())) return "";
        if ("COURIER".equalsIgnoreCase(m.deliveryMethod())) {
            long approxDoor = m.subtotalCents() + m.taxCents() + m.courierEstimateCents();
            return "<br/>Approx. total on delivery (to the courier): " + lkr(approxDoor);
        }
        return "<br/>Cash on delivery: " + lkr(m.deliveryCodCents());
    }

    private static String lkr(long cents) {
        return String.format(Locale.US, "Rs %.2f", cents / 100.0);
    }
}
