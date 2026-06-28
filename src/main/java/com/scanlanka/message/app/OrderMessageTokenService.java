package com.scanlanka.message.app;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;

/** Short-lived guest tokens scoped to a single order (19 SEC-MSG-1). */
@Service
public class OrderMessageTokenService {

    private static final int TTL_HOURS = 24;

    private final byte[] secret;

    public OrderMessageTokenService(
        @Value("${app.message.token-secret:dev-only-order-message-token-secret-change-me}") String secret) {
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
    }

    public record Verified(long orderId, Instant expiresAt) {}

    public String issue(long orderId) {
        Instant exp = Instant.now().plus(TTL_HOURS, ChronoUnit.HOURS);
        String payload = orderId + "|" + exp.getEpochSecond();
        return encode(payload) + "." + sign(payload);
    }

    public Verified verify(String token) {
        if (token == null || token.isBlank()) {
            throw unauthorized();
        }
        int dot = token.lastIndexOf('.');
        if (dot <= 0) throw unauthorized();
        String payload = decode(token.substring(0, dot));
        String sig = token.substring(dot + 1);
        if (!MessageDigest.isEqual(sign(payload).getBytes(StandardCharsets.UTF_8),
            sig.getBytes(StandardCharsets.UTF_8))) {
            throw unauthorized();
        }
        String[] parts = payload.split("\\|");
        if (parts.length != 2) throw unauthorized();
        long orderId = Long.parseLong(parts[0]);
        Instant exp = Instant.ofEpochSecond(Long.parseLong(parts[1]));
        if (Instant.now().isAfter(exp)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "TOKEN_EXPIRED");
        }
        return new Verified(orderId, exp);
    }

    private String sign(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String encode(String payload) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(payload.getBytes(StandardCharsets.UTF_8));
    }

    private static String decode(String encoded) {
        return new String(Base64.getUrlDecoder().decode(encoded), StandardCharsets.UTF_8);
    }

    private static ResponseStatusException unauthorized() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED");
    }
}
