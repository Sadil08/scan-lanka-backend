package com.scanlanka.shared.ratelimit;

import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;

/**
 * Fixed-window limiter in Redis (INCR + EXPIRE). **Fail-closed** (global/02 §4): if Redis is
 * unavailable, deny — so auth endpoints can't be brute-forced during a Redis outage.
 */
@Component
public class RedisRateLimiter implements RateLimiter {

    private final StringRedisTemplate redis;

    public RedisRateLimiter(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public void check(String key, int limit, int windowSeconds) {
        String redisKey = "rl:" + key;
        try {
            Long count = redis.opsForValue().increment(redisKey);
            if (count != null && count == 1L) {
                redis.expire(redisKey, Duration.ofSeconds(windowSeconds));
            }
            if (count != null && count > limit) {
                throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Too many requests");
            }
        } catch (DataAccessException e) {
            // fail-closed: a Redis outage must not open the brute-force window
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Rate limiter unavailable");
        }
    }
}
