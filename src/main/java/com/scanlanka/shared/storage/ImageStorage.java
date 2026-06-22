package com.scanlanka.shared.storage;

/**
 * Backend image store port (global/09 DIP; global/02 §8). Implementations store bytes outside the web
 * root under a random key and never reflect a client filename (T-20). Used by catalog images now;
 * reusable for banners (`14`) and bank slips (`06`).
 */
public interface ImageStorage {

    record StoredImage(String key, String url) {}

    /** Persist already-validated/re-encoded bytes; returns the random key + the URL to serve it. */
    StoredImage store(byte[] bytes, String extension);

    /** Load by key (key is validated to contain no path separators / traversal). */
    byte[] load(String key);
}
