package com.scanlanka.auth.app;

import com.scanlanka.auth.domain.RefreshToken;
import com.scanlanka.auth.infra.RefreshTokenRepository;
import com.scanlanka.shared.security.Hashing;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;

/**
 * Rotating refresh tokens (07 FR-AUTH-12). Stored as SHA-256 (high-entropy → deterministic lookup).
 * Reuse of an already-rotated token => the chain is revoked (theft response).
 */
@Service
public class RefreshTokenService {

    private static final SecureRandom RNG = new SecureRandom();
    private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();

    private final RefreshTokenRepository repo;
    private final AuthProperties props;

    public RefreshTokenService(RefreshTokenRepository repo, AuthProperties props) {
        this.repo = repo;
        this.props = props;
    }

    public record Rotation(Long userId, String rawToken) {}

    @Transactional
    public String issue(Long userId) {
        String raw = randomToken();
        repo.save(new RefreshToken(userId, Hashing.sha256Hex(raw), expiry()));
        return raw;
    }

    @Transactional
    public Rotation rotate(String rawOld) {
        RefreshToken old = repo.findByTokenHash(Hashing.sha256Hex(rawOld))
            .orElseThrow(RefreshTokenService::invalid);
        if (old.isRevoked()) {                       // reuse of a rotated token → revoke the chain
            repo.revokeAllForUser(old.getUserId());
            throw invalid();
        }
        if (Instant.now().isAfter(old.getExpiresAt())) throw invalid();

        String raw = randomToken();
        RefreshToken fresh = repo.save(new RefreshToken(old.getUserId(), Hashing.sha256Hex(raw), expiry()));
        old.revoke();
        old.setReplacedBy(fresh.getId());
        repo.save(old);
        return new Rotation(old.getUserId(), raw);
    }

    @Transactional
    public void revoke(String rawToken) {
        if (rawToken == null) return;
        repo.findByTokenHash(Hashing.sha256Hex(rawToken)).ifPresent(t -> {
            t.revoke();
            repo.save(t);
        });
    }

    @Transactional
    public void revokeAllForUser(Long userId) {
        repo.revokeAllForUser(userId);
    }

    private Instant expiry() {
        return Instant.now().plusSeconds(props.refreshTtlDays() * 86_400L);
    }

    private static String randomToken() {
        byte[] b = new byte[32];
        RNG.nextBytes(b);
        return B64.encodeToString(b);
    }

    private static ResponseStatusException invalid() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid session");
    }
}
