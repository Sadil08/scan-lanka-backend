package com.scanlanka.auth;

import com.scanlanka.auth.app.AuthProperties;
import com.scanlanka.auth.app.RefreshTokenService;
import com.scanlanka.auth.domain.RefreshToken;
import com.scanlanka.auth.infra.RefreshTokenRepository;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Refresh rotation + reuse detection (07 T-5). */
class RefreshRotationTest {

    private final RefreshTokenRepository repo = mock(RefreshTokenRepository.class);
    private final AuthProperties props = new AuthProperties(
        "secret-key-32-bytes-minimum-for-testing-x", "scanlanka", "scanlanka-web",
        15, 14, true, "sl_at", "sl_rt", 10, 5, "", "", true);
    private final RefreshTokenService service = new RefreshTokenService(repo, props);

    @Test
    void rotateIssuesNewTokenAndRevokesOld() {
        RefreshToken old = new RefreshToken(1L, hash("old-raw"), Instant.now().plusSeconds(3600));
        when(repo.findByTokenHash(hash("old-raw"))).thenReturn(Optional.of(old));
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        RefreshTokenService.Rotation rotation = service.rotate("old-raw");
        assertThat(rotation.userId()).isEqualTo(1L);
        assertThat(rotation.rawToken()).isNotBlank();
        assertThat(old.isRevoked()).isTrue();
    }

    @Test
    void replayOfRotatedTokenRevokesChain() {
        RefreshToken old = new RefreshToken(1L, hash("stolen"), Instant.now().plusSeconds(3600));
        old.revoke();
        when(repo.findByTokenHash(hash("stolen"))).thenReturn(Optional.of(old));

        assertThatThrownBy(() -> service.rotate("stolen"))
            .isInstanceOf(ResponseStatusException.class);
        verify(repo).revokeAllForUser(1L);
    }

    private static String hash(String raw) {
        return com.scanlanka.shared.security.Hashing.sha256Hex(raw);
    }
}
