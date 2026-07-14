package com.scanlanka.shared.branding;

import java.io.IOException;
import java.io.InputStream;
import java.util.Base64;

/**
 * Shared branding constants for generated PDFs and emails (09 FR-18, 10 FR-NOTIFY) - one source of
 * truth so the receipt PDF and every transactional email use the same logo and brand blue as the
 * storefront ({@code frontend/src/styles/tokens.css} --primary/--primary-dark/--primary-light).
 *
 * <p>PDF receipts embed the logo as a base64 data URI (openhtmltopdf does not fetch URLs). HTML
 * emails use a CID reference ({@link #LOGO_CID_URI}) with the PNG attached inline by
 * {@link com.scanlanka.notification.app.SmtpEmailProvider} — many clients (e.g. Gmail) strip or hide
 * {@code data:} images in mail.
 */
public final class BrandAssets {

    private BrandAssets() {}

    public static final String PRIMARY = "#1a6db5";
    public static final String PRIMARY_DARK = "#12527f";
    public static final String PRIMARY_LIGHT = "#eaf2fa";
    public static final String INK = "#111111";
    public static final String MUTED = "#5a6b76";

    public static final String COMPANY_NAME = "Scan Lanka Trading Co. (Pvt) Ltd";
    public static final String COMPANY_ADDRESS = "No 385, Kaduwela Road, Malabe, Sri Lanka";
    public static final String COMPANY_PHONE = "071 781 7447";
    public static final String COMPANY_EMAIL = "scanlankagroup.info@gmail.com";

    /** Content-ID for the inline logo MIME part (quoted-string form without angle brackets). */
    public static final String LOGO_CID = "scanlanka-logo";

    /** {@code <img src>} value for HTML emails that attach {@link #LOGO_PNG_BYTES} as inline. */
    public static final String LOGO_CID_URI = "cid:" + LOGO_CID;

    public static final byte[] LOGO_PNG_BYTES = loadLogoBytes();

    /** Data URI for PDF/HTML contexts that cannot use MIME CID (receipt PDF). */
    public static final String LOGO_DATA_URI = toDataUri(LOGO_PNG_BYTES);

    private static byte[] loadLogoBytes() {
        try (InputStream in = BrandAssets.class.getResourceAsStream("/branding/logo.png")) {
            if (in == null) return new byte[0];
            return in.readAllBytes();
        } catch (IOException e) {
            return new byte[0]; // missing logo must never break receipt/email rendering
        }
    }

    private static String toDataUri(byte[] bytes) {
        if (bytes.length == 0) return "";
        return "data:image/png;base64," + Base64.getEncoder().encodeToString(bytes);
    }
}
