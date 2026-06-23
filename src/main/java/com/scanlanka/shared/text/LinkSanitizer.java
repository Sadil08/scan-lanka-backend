package com.scanlanka.shared.text;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Banner link allow-list (14 SEC-MERCH-1). */
public final class LinkSanitizer {

    private LinkSanitizer() {}

    public static String sanitize(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String link = raw.trim();
        String lower = link.toLowerCase();
        if (lower.startsWith("javascript:") || lower.startsWith("data:") || lower.startsWith("vbscript:")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_LINK");
        }
        if (link.startsWith("/")) return link;
        if (lower.startsWith("http://") || lower.startsWith("https://")) return link;
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_LINK");
    }
}
