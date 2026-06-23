package com.scanlanka.shared.text;

import org.owasp.html.HtmlPolicyBuilder;
import org.owasp.html.PolicyFactory;
import org.springframework.stereotype.Component;

/** OWASP HTML sanitizer for CMS bodies (15 SEC-CONTENT-1). */
@Component
public class HtmlSanitizer {

    private final PolicyFactory policy = new HtmlPolicyBuilder()
        .allowElements("p", "br", "strong", "em", "ul", "ol", "li", "h2", "h3", "a", "blockquote")
        .allowAttributes("href").onElements("a")
        .allowStandardUrlProtocols()
        .toFactory();

    public String sanitize(String html) {
        if (html == null) return "";
        return policy.sanitize(html);
    }
}
