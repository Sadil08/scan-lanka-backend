package com.scanlanka.content.app;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Component;

@Component
public class ContentCacheEvictor {

    @CacheEvict(value = "content", key = "#slug")
    public void evict(String slug) {}
}
