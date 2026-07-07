package com.scanlanka.shared.branding;

import java.io.IOException;
import java.io.InputStream;
import java.util.Base64;

/**
 * Shared branding constants for generated PDFs and emails (09 FR-18, 10 FR-NOTIFY) - one source of
 * truth so the receipt PDF and every transactional email use the same logo and brand blue as the
 * storefront ({@code frontend/src/styles/tokens.css} --primary/--primary-dark/--primary-light).
 *
 * <p>The logo is embedded as a base64 data URI, not linked, so it renders identically in both
 * contexts: the PDF renderer (openhtmltopdf) never fetches external resources, and a data URI is the
 * one image-embedding technique that works across mainstream email clients without adding multipart/
 * CID attachment plumbing to {@link com.scanlanka.notification.app.EmailProvider}.
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

    public static final String LOGO_DATA_URI = loadLogoDataUri();

    private static String loadLogoDataUri() {
        try (InputStream in = BrandAssets.class.getResourceAsStream("/branding/logo.png")) {
            if (in == null) return "";
            byte[] bytes = in.readAllBytes();
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(bytes);
        } catch (IOException e) {
            return ""; // a missing/unreadable logo must never break receipt/email rendering
        }
    }
}
