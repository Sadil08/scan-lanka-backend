package com.scanlanka.shared.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Read-through caches for catalog/geo/content/delivery lookups (P0-4).
 *
 * Caffeine, not a plain ConcurrentMap: every cache is bounded (maxSize) and
 * TTL'd (expireAfterWrite),
 * so memory can't grow unboundedly and stale entries self-evict even if an
 * explicit evict is missed.
 * Stays in-process deliberately — {@code catalog-list} caches a Spring
 * {@code Page}, which the Redis
 * JSON serializer cannot round-trip; a single-instance monolith does not need a
 * shared cache yet.
 * Admin edits still call the cache evictors for immediate invalidation.
 */
@Configuration
@EnableCaching
public class CatalogCacheConfig {

    /** Default eviction for fast-changing catalog/content data. */
    private static final Duration DEFAULT_TTL = Duration.ofMinutes(15);
    private static final long DEFAULT_MAX_SIZE = 10_000;

    @Bean
    CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager();
        manager.setCacheNames(java.util.List.of(
                "catalog-list", "catalog-detail", "catalog-facets", "home", "content"));
        manager.setCaffeine(Caffeine.newBuilder()
                .maximumSize(DEFAULT_MAX_SIZE)
                .expireAfterWrite(DEFAULT_TTL));

        // Geo + delivery reference data change rarely — longer TTLs.
        manager.registerCustomCache("geo", build(Duration.ofHours(1), 50_000));
        manager.registerCustomCache("delivery-postal", build(Duration.ofHours(6), 50_000));
        manager.registerCustomCache("delivery-locations", build(Duration.ofHours(6), 1_000));
        return manager;
    }

    private static com.github.benmanes.caffeine.cache.Cache<Object, Object> build(Duration ttl, long maxSize) {
        return Caffeine.newBuilder().maximumSize(maxSize).expireAfterWrite(ttl).build();
    }
}
