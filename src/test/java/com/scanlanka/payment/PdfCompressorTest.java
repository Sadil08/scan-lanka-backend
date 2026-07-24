package com.scanlanka.payment;

import com.scanlanka.payment.app.PdfCompressor;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

class PdfCompressorTest {

    private final PdfCompressor compressor = new PdfCompressor();

    /** A big PNG embedded in a PDF (like a phone-scanned slip) should shrink and stay a valid PDF. */
    @Test
    void compressesAnImageHeavyPdf() throws Exception {
        byte[] original = pdfWithLargeImage();
        byte[] compressed = compressor.compress(original);

        assertThat(compressed.length).isLessThan(original.length);
        try (PDDocument doc = PDDocument.load(compressed)) {
            assertThat(doc.getNumberOfPages()).isEqualTo(1);   // page preserved
        }
    }

    /** Non-PDF / unparseable bytes fall back to the original untouched (never throws). */
    @Test
    void fallsBackWhenNotAPdf() {
        byte[] junk = "not a pdf".getBytes();
        assertThat(compressor.compress(junk)).isEqualTo(junk);
    }

    private static byte[] pdfWithLargeImage() throws Exception {
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);
            // Per-pixel pseudo-random noise: incompressible, so the losslessly-embedded original PDF is
            // genuinely large (like a high-res phone scan). Rasterizing to 150 DPI + JPEG must shrink it.
            BufferedImage img = new BufferedImage(1800, 2400, BufferedImage.TYPE_INT_RGB);
            java.util.Random rnd = new java.util.Random(42);
            for (int y = 0; y < img.getHeight(); y++) {
                for (int x = 0; x < img.getWidth(); x++) {
                    img.setRGB(x, y, rnd.nextInt(0xFFFFFF));
                }
            }
            PDImageXObject xImg = LosslessFactory.createFromImage(doc, img);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.drawImage(xImg, 0, 0, PDRectangle.A4.getWidth(), PDRectangle.A4.getHeight());
            }
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            doc.save(bos);
            return bos.toByteArray();
        }
    }
}
