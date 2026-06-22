package com.scanlanka.order.app;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Order config (09). Signing secret from env (global/02 §5) — never commit a real value. */
@ConfigurationProperties(prefix = "app.order")
public record OrderProperties(String signingSecret) {
}
