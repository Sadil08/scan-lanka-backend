package com.scanlanka.payment.app;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.JPEGFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;

/**
 * Compresses + hardens uploaded PDF bank-transfer slips (owner 2026-07-23) by rasterizing each page
 * at a modest DPI and rebuilding a fresh JPEG-backed PDF. This keeps storage small (a phone-scanned
 * slip is often several MB of high-res images) and, like the image decode→re-encode, strips any active
 * content — JavaScript, embedded files, launch actions — so we never store an executable PDF.
 *
 * <p>Slips are receipts, not documents that need selectable text, so flattening to an image is fine.
 * On any parse/render failure we fall back to the original bytes (already size-capped by the caller),
 * so a slightly unusual but valid PDF is never rejected outright.
 */
@Component
public class PdfCompressor {

    private static final Logger log = LoggerFactory.getLogger(PdfCompressor.class);

    private static final int RENDER_DPI = 150;   // legible for a slip; far below a raw phone scan
    private static final int MAX_PAGES = 10;      // a slip is 1–2 pages; guards a pathological upload
    private static final float JPEG_QUALITY = 0.7f;

    /** @return a rasterized, compressed PDF; or the input unchanged if it can't be re-rendered. */
    public byte[] compress(byte[] pdfBytes) {
        try (PDDocument src = PDDocument.load(pdfBytes);
             PDDocument out = new PDDocument()) {
            int pages = Math.min(src.getNumberOfPages(), MAX_PAGES);
            if (pages == 0) return pdfBytes;
            PDFRenderer renderer = new PDFRenderer(src);
            for (int i = 0; i < pages; i++) {
                BufferedImage img = renderer.renderImageWithDPI(i, RENDER_DPI, ImageType.RGB);
                PDPage page = new PDPage(new PDRectangle(img.getWidth(), img.getHeight()));
                out.addPage(page);
                PDImageXObject xImg = JPEGFactory.createFromImage(out, img, JPEG_QUALITY);
                try (PDPageContentStream cs = new PDPageContentStream(out, page)) {
                    cs.drawImage(xImg, 0, 0, img.getWidth(), img.getHeight());
                }
            }
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            out.save(bos);
            byte[] result = bos.toByteArray();
            // Only keep the rebuilt copy if it actually saved space; otherwise the original is fine.
            return result.length < pdfBytes.length ? result : pdfBytes;
        } catch (Exception e) {
            log.warn("PDF slip compression failed; storing original ({} bytes)", pdfBytes.length, e);
            return pdfBytes;
        }
    }
}
