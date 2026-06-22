package com.scanlanka.auth;

import com.scanlanka.auth.app.TotpService;
import dev.samstevens.totp.code.DefaultCodeGenerator;
import dev.samstevens.totp.time.SystemTimeProvider;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TotpServiceTest {

    private final TotpService totp = new TotpService();

    @Test
    void verifiesAValidCurrentCode() throws Exception {
        String secret = totp.generateSecret();
        long counter = Math.floorDiv(new SystemTimeProvider().getTime(), 30);
        String code = new DefaultCodeGenerator().generate(secret, counter);

        assertThat(totp.verify(secret, code)).isTrue();
    }

    @Test
    void rejectsInvalidCode() {
        String secret = totp.generateSecret();
        assertThat(totp.verify(secret, null)).isFalse();
        assertThat(totp.verify(secret, "12")).isFalse();   // wrong format
    }
}
