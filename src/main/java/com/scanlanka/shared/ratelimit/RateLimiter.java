package com.scanlanka.shared.ratelimit;

/** Rate limiting (global/02 §4, global/08 T-9/T-13). Throws 429 when exceeded; fail-closed. */
public interface RateLimiter {

    /** @throws org.springframework.web.server.ResponseStatusException 429 if over the limit. */
    void check(String key, int limit, int windowSeconds);
}
