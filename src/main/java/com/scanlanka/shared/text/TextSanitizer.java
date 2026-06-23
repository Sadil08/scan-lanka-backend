package com.scanlanka.shared.text;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Sanitize free-text user input (12/11 SEC). */
public final class TextSanitizer {

    private TextSanitizer() {}

    public static String plain(String raw, int maxLen, String field) {
        if (raw == null || raw.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_" + field);
        }
        String s = raw.replace("\r", "").replace("\0", "").trim();
        if (s.length() > maxLen) s = s.substring(0, maxLen);
        if (s.contains("\n\n\n")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_" + field);
        }
        return s;
    }

    public static String optional(String raw, int maxLen) {
        if (raw == null || raw.isBlank()) return null;
        String s = raw.replace("\r", "").replace("\0", "").trim();
        return s.length() > maxLen ? s.substring(0, maxLen) : s;
    }

    /** Block email header injection in contact forms. */
    public static String email(String raw) {
        String s = plain(raw, 254, "EMAIL").toLowerCase();
        if (s.contains("\n") || s.contains(":")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_EMAIL");
        }
        return s;
    }
}
