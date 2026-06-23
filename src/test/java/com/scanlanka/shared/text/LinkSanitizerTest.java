package com.scanlanka.shared.text;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LinkSanitizerTest {

    @Test
    void rejectsJavascript() {
        org.junit.jupiter.api.Assertions.assertThrows(
            org.springframework.web.server.ResponseStatusException.class,
            () -> LinkSanitizer.sanitize("javascript:alert(1)"));
    }

    @Test
    void allowsHttpsAndPaths() {
        assertThat(LinkSanitizer.sanitize("https://scanlanka.com")).startsWith("https://");
        assertThat(LinkSanitizer.sanitize("/products")).isEqualTo("/products");
    }
}
