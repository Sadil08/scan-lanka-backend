package com.scanlanka.auth;

import com.scanlanka.auth.app.AuthProperties;
import com.scanlanka.auth.app.OneTimeCodeService;
import com.scanlanka.auth.domain.OneTimeCode;
import com.scanlanka.auth.domain.OneTimeCode.Purpose;
import com.scanlanka.auth.infra.OneTimeCodeRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OneTimeCodeServiceTest {

    private final OneTimeCodeRepository repo = mock(OneTimeCodeRepository.class);
    private final PasswordEncoder encoder = new BCryptPasswordEncoder();
    private final AuthProperties props = new AuthProperties(
        "secret-key-32-bytes-minimum-for-testing-x", "scanlanka", "scanlanka-web",
        15, 14, true, "sl_at", "sl_rt", 10, 3);
    private final OneTimeCodeService service = new OneTimeCodeService(repo, encoder, props);

    @Test
    void issuesSixDigitCodeAndStoresHash() {
        when(repo.save(any())).thenAnswer(i -> i.getArgument(0));
        String code = service.issue(1L, Purpose.PASSWORD_RESET);
        assertThat(code).hasSize(6).containsOnlyDigits();
    }

    @Test
    void verifiesCorrectCodeAndConsumes() {
        OneTimeCode otc = new OneTimeCode(1L, Purpose.PASSWORD_RESET,
            encoder.encode("123456"), Instant.now().plusSeconds(600));
        when(repo.findFirstByUserIdAndPurposeAndConsumedFalseOrderByCreatedAtDesc(1L, Purpose.PASSWORD_RESET))
            .thenReturn(Optional.of(otc));
        when(repo.save(any())).thenAnswer(i -> i.getArgument(0));

        assertThat(service.verify(1L, Purpose.PASSWORD_RESET, "123456")).isTrue();
        assertThat(otc.isConsumed()).isTrue();
    }

    @Test
    void rejectsWrongCodeAndIncrementsAttempts() {
        OneTimeCode otc = new OneTimeCode(1L, Purpose.PASSWORD_RESET,
            encoder.encode("123456"), Instant.now().plusSeconds(600));
        when(repo.findFirstByUserIdAndPurposeAndConsumedFalseOrderByCreatedAtDesc(1L, Purpose.PASSWORD_RESET))
            .thenReturn(Optional.of(otc));
        when(repo.save(any())).thenAnswer(i -> i.getArgument(0));

        assertThat(service.verify(1L, Purpose.PASSWORD_RESET, "000000")).isFalse();
        assertThat(otc.getAttempts()).isEqualTo(1);
        assertThat(otc.isConsumed()).isFalse();
    }

    @Test
    void rejectsExpiredCode() {
        OneTimeCode expired = new OneTimeCode(1L, Purpose.EMAIL_VERIFY,
            encoder.encode("123456"), Instant.now().minusSeconds(1));
        when(repo.findFirstByUserIdAndPurposeAndConsumedFalseOrderByCreatedAtDesc(1L, Purpose.EMAIL_VERIFY))
            .thenReturn(Optional.of(expired));

        assertThat(service.verify(1L, Purpose.EMAIL_VERIFY, "123456")).isFalse();
    }
}
