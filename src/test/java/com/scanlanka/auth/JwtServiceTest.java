package com.scanlanka.auth;

import com.scanlanka.auth.app.AuthProperties;
import com.scanlanka.auth.app.JwtService;
import com.scanlanka.auth.domain.AppUser;
import com.scanlanka.auth.domain.Role;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private final AuthProperties props = new AuthProperties(
        "test-secret-please-rotate-32+bytes-minimum-key", "scanlanka", "scanlanka-web",
        15, 14, true, "sl_at", "sl_rt", 10, 5, "", "", true);
    private final JwtService jwt = new JwtService(props);

    private AppUser admin() {
        AppUser u = new AppUser("a@x.lk", "hash", "Admin", Role.ADMIN);
        // id is normally DB-generated; set via reflection-free path by re-using a minimal stub
        return new AppUserWithId(1L, u);
    }

    @Test
    void issuesAndParsesRoundTrip() {
        String token = jwt.issueAccessToken(admin());
        JwtService.AccessClaims c = jwt.parse(token);
        assertThat(c.userId()).isEqualTo(1L);
        assertThat(c.role()).isEqualTo(Role.ADMIN);
        assertThat(c.tokenVersion()).isZero();
    }

    @Test
    void rejectsTamperedToken() {
        String token = jwt.issueAccessToken(admin());
        String tampered = token.substring(0, token.length() - 2) + "xx";
        assertThatThrownBy(() -> jwt.parse(tampered))
            .isInstanceOf(JwtService.InvalidTokenException.class);
    }

    @Test
    void rejectsWrongSecret() {
        String token = jwt.issueAccessToken(admin());
        AuthProperties other = new AuthProperties(
            "a-completely-different-secret-key-32+bytes-x", "scanlanka", "scanlanka-web",
            15, 14, true, "sl_at", "sl_rt", 10, 5, "", "", true);
        JwtService otherJwt = new JwtService(other);
        assertThatThrownBy(() -> otherJwt.parse(token))
            .isInstanceOf(JwtService.InvalidTokenException.class);
    }

    /** Test-only AppUser with a fixed id (id is otherwise DB-generated). */
    static class AppUserWithId extends AppUser {
        private final Long id;
        AppUserWithId(Long id, AppUser src) {
            super(src.getEmail(), src.getPasswordHash(), src.getName(), src.getRole());
            this.id = id;
        }
        @Override public Long getId() { return id; }
    }
}
