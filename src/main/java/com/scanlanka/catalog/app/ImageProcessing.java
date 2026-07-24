package com.scanlanka.catalog.app;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * Hardens uploaded images (01 NFR-CATALOG-1, global/08 T-6/T-21): magic-byte check, size cap, and a
 * **decode → re-encode** that destroys polyglots and strips EXIF metadata.
 *
 * <p>Output is <b>JPEG</b> (owner 2026-07-22). We previously re-encoded to PNG, which for photographic
 * product shots is ~9× larger than the source JPEG and made the storefront slow to load — every card
 * pulled a multi-MB PNG from the backend through the Vercel proxy (no CDN). JPEG keeps the same
 * decode→re-encode hardening (full pixel decode strips any embedded payload and EXIF) at a fraction of
 * the bytes. Transparency is flattened onto white since JPEG has no alpha.
 *
 * <p>Uploads are also <b>downscaled</b> to a max long edge (owner 2026-07-23) so a phone photo or a
 * huge scan doesn't sit on disk at full resolution — the storefront never displays wider than the
 * gallery/lightbox anyway. Applies to product images and image bank-transfer slips alike.
 */
@Component
public class ImageProcessing {

    private static final long MAX_BYTES = 5L * 1024 * 1024; // 5MB
    private static final float JPEG_QUALITY = 0.82f;        // visually lossless for web at card/gallery sizes
    private static final int MAX_EDGE = 1600;               // long-edge cap; gallery/lightbox never need more

    /** @return re-encoded JPEG bytes. @throws 413/415 on oversize/non-image. */
    public byte[] validateAndReencode(byte[] input) {
        if (input == null || input.length == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "EMPTY_FILE");
        }
        if (input.length > MAX_BYTES) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "FILE_TOO_LARGE");
        }
        if (!isSupportedImage(input)) {
            throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_IMAGE");
        }
        try {
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(input));
            if (img == null) {
                throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_IMAGE");
            }
            return encodeJpeg(downscale(flattenToRgb(img)), JPEG_QUALITY); // re-encode → no EXIF, no embedded payloads
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_IMAGE");
        }
    }

    public String outputExtension() {
        return "jpg";
    }

    /** JPEG can't carry alpha — composite over white so PNG/GIF transparency doesn't turn black. */
    private static BufferedImage flattenToRgb(BufferedImage src) {
        if (src.getType() == BufferedImage.TYPE_INT_RGB && !src.getColorModel().hasAlpha()) {
            return src;
        }
        BufferedImage rgb = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = rgb.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, src.getWidth(), src.getHeight());
        g.drawImage(src, 0, 0, null);
        g.dispose();
        return rgb;
    }

    /** Shrink to MAX_EDGE on the long side (keeps aspect); returns the source untouched if already within. */
    private static BufferedImage downscale(BufferedImage src) {
        int w = src.getWidth(), h = src.getHeight();
        int longEdge = Math.max(w, h);
        if (longEdge <= MAX_EDGE) return src;
        double scale = (double) MAX_EDGE / longEdge;
        int nw = Math.max(1, (int) Math.round(w * scale));
        int nh = Math.max(1, (int) Math.round(h * scale));
        BufferedImage out = new BufferedImage(nw, nh, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.drawImage(src, 0, 0, nw, nh, null);
        g.dispose();
        return out;
    }

    private static byte[] encodeJpeg(BufferedImage img, float quality) throws IOException {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        ImageWriteParam param = writer.getDefaultWriteParam();
        param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        param.setCompressionQuality(quality);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ImageOutputStream ios = ImageIO.createImageOutputStream(out)) {
            writer.setOutput(ios);
            writer.write(null, new IIOImage(img, null, null), param);
        } finally {
            writer.dispose();
        }
        return out.toByteArray();
    }

    /** Magic-byte sniff (not by client extension) — JPEG / PNG / GIF / WEBP. */
    private boolean isSupportedImage(byte[] b) {
        if (b.length < 12) return false;
        boolean jpeg = (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8;
        boolean png = (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G';
        boolean gif = b[0] == 'G' && b[1] == 'I' && b[2] == 'F';
        boolean webp = b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
            && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P';
        return jpeg || png || gif || webp;
    }
}
