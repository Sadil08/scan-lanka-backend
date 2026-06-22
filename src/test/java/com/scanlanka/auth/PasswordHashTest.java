package com.scanlanka.auth;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;

/** Password hashing (07 T-3, NFR-AUTH-1). */
class PasswordHashTest {

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(12);

    @Test
    void hashesAndVerifiesWithoutStoringPlaintext() {
        String hash = encoder.encode("password123");
        assertThat(hash).doesNotContain("password123");
        assertThat(encoder.matches("password123", hash)).isTrue();
        assertThat(encoder.matches("wrong", hash)).isFalse();
    }
}
