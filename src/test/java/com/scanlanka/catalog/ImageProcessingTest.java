package com.scanlanka.catalog;

import com.scanlanka.catalog.app.ImageProcessing;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ImageProcessingTest {

    private final ImageProcessing processing = new ImageProcessing();

    private static byte[] tinyPng() throws Exception {
        BufferedImage img = new BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        return out.toByteArray();
    }

    @Test
    void reencodesAValidImageToJpeg() throws Exception {
        byte[] result = processing.validateAndReencode(tinyPng());
        // JPEG magic bytes (re-encoded → EXIF/polyglot stripped, T-21). Output is JPEG (owner 2026-07-22)
        // instead of PNG so photographic product shots are ~9× smaller on the storefront.
        assertThat(result).isNotEmpty();
        assertThat(result[0] & 0xFF).isEqualTo(0xFF);
        assertThat(result[1] & 0xFF).isEqualTo(0xD8);
        assertThat(processing.outputExtension()).isEqualTo("jpg");
    }

    @Test
    void downscalesLargeImagesToMaxEdge() throws Exception {
        BufferedImage big = new BufferedImage(4000, 3000, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(big, "png", out);
        byte[] result = processing.validateAndReencode(out.toByteArray());
        BufferedImage decoded = ImageIO.read(new java.io.ByteArrayInputStream(result));
        assertThat(Math.max(decoded.getWidth(), decoded.getHeight())).isEqualTo(1600); // capped long edge
        assertThat(decoded.getWidth()).isEqualTo(1600);
        assertThat(decoded.getHeight()).isEqualTo(1200); // aspect preserved
    }

    @Test
    void rejectsNonImageBytes() {
        assertThatThrownBy(() -> processing.validateAndReencode("not an image, just text".getBytes()))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("UNSUPPORTED_IMAGE");
    }

    @Test
    void rejectsEmpty() {
        assertThatThrownBy(() -> processing.validateAndReencode(new byte[0]))
            .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void rejectsOversizeFile() {
        byte[] huge = new byte[6 * 1024 * 1024]; // > 5MB
        huge[0] = (byte) 0xFF; huge[1] = (byte) 0xD8; // pretend JPEG header
        assertThatThrownBy(() -> processing.validateAndReencode(huge))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("FILE_TOO_LARGE");
    }
}
