package com.scanlanka.order.app.receipt;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;

/** HTML→PDF with no external resource fetching (09 T-21). */
@Component
public class ReceiptPdfRenderer {

    private final ReceiptHtmlRenderer htmlRenderer;

    public ReceiptPdfRenderer(ReceiptHtmlRenderer htmlRenderer) {
        this.htmlRenderer = htmlRenderer;
    }

    public byte[] render(ReceiptModel model) {
        String html = htmlRenderer.render(model);
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            builder.withHtmlContent(html, null);
            builder.toStream(out);
            builder.run();
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("PDF render failed", e);
        }
    }
}
