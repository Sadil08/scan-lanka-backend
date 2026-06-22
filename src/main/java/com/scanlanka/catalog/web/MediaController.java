package com.scanlanka.catalog.web;

import com.scanlanka.shared.storage.ImageStorage;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

/** Serves stored images via an authz'd controller (not a static path) — T-20/T-6. Public read, cached. */
@RestController
public class MediaController {

    private final ImageStorage storage;

    public MediaController(ImageStorage storage) {
        this.storage = storage;
    }

    @GetMapping("/api/media/{key}")
    public ResponseEntity<byte[]> get(@PathVariable String key) {
        byte[] data = storage.load(key);  // key validated against traversal inside storage
        return ResponseEntity.ok()
            .contentType(MediaType.IMAGE_PNG)
            .cacheControl(CacheControl.maxAge(Duration.ofDays(1)).cachePublic())
            .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
            .header("X-Content-Type-Options", "nosniff")
            .body(data);
    }
}
