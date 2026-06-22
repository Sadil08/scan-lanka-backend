package com.scanlanka.shared.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/**
 * Stores images on the local filesystem under a configured dir (outside the web root). Random keys
 * only — never a client filename (T-20). Served via MediaController, not as static paths.
 */
@Component
public class FilesystemImageStorage implements ImageStorage {

    private final Path baseDir;

    public FilesystemImageStorage(@Value("${app.storage.dir}") String dir) {
        this.baseDir = Path.of(dir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(baseDir);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public StoredImage store(byte[] bytes, String extension) {
        String key = UUID.randomUUID() + "." + sanitizeExt(extension);
        try {
            Files.write(resolveSafe(key), bytes);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return new StoredImage(key, "/api/media/" + key);
    }

    @Override
    public byte[] load(String key) {
        try {
            return Files.readAllBytes(resolveSafe(key));
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Not found");
        }
    }

    /** Reject anything that isn't a plain filename within baseDir (anti path-traversal, T-20). */
    private Path resolveSafe(String key) {
        if (key == null || key.contains("/") || key.contains("\\") || key.contains("..")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid key");
        }
        Path resolved = baseDir.resolve(key).normalize();
        if (!resolved.startsWith(baseDir)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid key");
        }
        return resolved;
    }

    private static String sanitizeExt(String ext) {
        return (ext != null && ext.matches("[a-z0-9]{1,5}")) ? ext : "png";
    }
}
