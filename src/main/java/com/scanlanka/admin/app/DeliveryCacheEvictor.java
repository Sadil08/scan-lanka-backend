package com.scanlanka.admin.app;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Component;

/** Evicts checkout delivery caches after admin config changes (08 FR-ADMIN-1). */
@Component
public class DeliveryCacheEvictor {

    @CacheEvict(value = "delivery-postal", allEntries = true)
    public void evictPostal() {}

    @CacheEvict(value = "delivery-locations", allEntries = true)
    public void evictLocations() {}

    public void evictAll() {
        evictPostal();
        evictLocations();
    }
}
