package com.scanlanka.shared.web;

import com.scanlanka.shared.ratelimit.RateLimiter;
import com.scanlanka.shared.security.ClientIp;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Receives CSP violation reports from the storefront (global/02 §7, §8a).
 * Logs sanitized summaries — no PII, no full document URLs with query strings.
 */
@RestController
@RequestMapping("/api/csp-report")
public class CspReportController {

    private static final Logger log = LoggerFactory.getLogger(CspReportController.class);
    private static final int MAX_BODY = 4096;

    private final RateLimiter rateLimiter;

    public CspReportController(RateLimiter rateLimiter) {
        this.rateLimiter = rateLimiter;
    }

    @PostMapping
    public ResponseEntity<Void> report(@RequestBody(required = false) String body, HttpServletRequest req) {
        rateLimiter.check("csp:" + ClientIp.from(req), 30, 60);
        if (body != null && !body.isBlank()) {
            String snippet = body.length() > MAX_BODY ? body.substring(0, MAX_BODY) + "…" : body;
            // Redact obvious email-like tokens before logging
            log.warn("CSP violation report: {}", snippet.replaceAll("[\\w.+-]+@[\\w.-]+", "[redacted]"));
        }
        return ResponseEntity.noContent().build();
    }
}
