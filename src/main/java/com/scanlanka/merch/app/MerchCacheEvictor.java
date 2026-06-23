package com.scanlanka.merch.app;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Component;

@Component
public class MerchCacheEvictor {

    @CacheEvict(value = "home", allEntries = true)
    public void evictHome() {}
}
