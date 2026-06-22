package com.scanlanka.shared.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

/** Phase 0 smoke endpoint — confirms the app boots and the chain is wired. Replaced by real features. */
@RestController
public class PingController {

    @GetMapping("/api/ping")
    public Map<String, Object> ping() {
        return Map.of("service", "scanlanka-backend", "status", "ok", "time", Instant.now().toString());
    }
}
