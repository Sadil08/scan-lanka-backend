package com.scanlanka.shared.text;

/** HTML-escape user-controlled strings for email/PDF templates (10 FR-NOTIFY-6, T-21). */
public final class HtmlEscaper {

    private HtmlEscaper() {}

    public static String escape(String raw) {
        if (raw == null) return "";
        return raw.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;")
            .replace("\r", "")
            .replace("\n", "<br/>");
    }

    /** Sanitize email subject lines (no CR/LF header injection). */
    public static String subject(String raw) {
        if (raw == null) return "";
        return raw.replace("\r", "").replace("\n", " ").trim();
    }
}
