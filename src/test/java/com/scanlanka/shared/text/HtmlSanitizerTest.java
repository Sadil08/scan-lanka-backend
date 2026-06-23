package com.scanlanka.shared.text;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HtmlSanitizerTest {

    private final HtmlSanitizer sanitizer = new HtmlSanitizer();

    @Test
    void stripsScriptAndIframe() {
        String out = sanitizer.sanitize("<p>Hi</p><script>alert(1)</script><iframe src=x></iframe>");
        assertThat(out).doesNotContain("script");
        assertThat(out).doesNotContain("iframe");
        assertThat(out).contains("Hi");
    }
}
