package com.scanlanka.catalog.app;

import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;

/** Clears storefront catalog caches after admin writes (01 T14, 02). */
@Service
public class CatalogCacheEvictor {

    private static final String[] CACHES = {"catalog-list", "catalog-detail", "catalog-facets"};

    private final CacheManager cacheManager;

    public CatalogCacheEvictor(CacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    public void evictAll() {
        for (String name : CACHES) {
            var cache = cacheManager.getCache(name);
            if (cache != null) cache.clear();
        }
    }
}
