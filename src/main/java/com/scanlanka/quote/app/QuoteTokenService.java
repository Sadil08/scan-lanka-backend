package com.scanlanka.quote.app;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HexFormat;

@Service
public class QuoteTokenService {

    private static final SecureRandom RNG = new SecureRandom();

    private final byte[] secret;

    public QuoteTokenService(@Value("${app.quote.token-secret:dev-only-quote-token-secret-change-me}") String secret) {
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
    }

    public String issue() {
        byte[] buf = new byte[24];
        RNG.nextBytes(buf);
        return HexFormat.of().formatHex(buf);
    }

    public String hash(String token) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(secret);
            md.update(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(md.digest());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public boolean matches(String token, String storedHash) {
        return storedHash != null && storedHash.equals(hash(token));
    }
}
