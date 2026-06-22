package com.scanlanka.auth.app;

import com.scanlanka.auth.domain.OneTimeCode;
import com.scanlanka.auth.domain.OneTimeCode.Purpose;
import com.scanlanka.auth.infra.OneTimeCodeRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;

/**
 * Email-verify / password-reset OTP (07 FR-AUTH-6/11, NFR-AUTH-3). Codes are random, hashed at rest,
 * short-expiry, single-use, attempt-capped. The raw code is returned once (to be emailed by `10`).
 */
@Service
public class OneTimeCodeService {

    private static final SecureRandom RNG = new SecureRandom();

    private final OneTimeCodeRepository repo;
    private final PasswordEncoder encoder;
    private final AuthProperties props;

    public OneTimeCodeService(OneTimeCodeRepository repo, PasswordEncoder encoder, AuthProperties props) {
        this.repo = repo;
        this.encoder = encoder;
        this.props = props;
    }

    /** Issues a 6-digit code, stores its hash, returns the raw code (caller emails it). */
    @Transactional
    public String issue(Long userId, Purpose purpose) {
        String code = String.format("%06d", RNG.nextInt(1_000_000));
        Instant expiresAt = Instant.now().plusSeconds(props.otpTtlMinutes() * 60L);
        repo.save(new OneTimeCode(userId, purpose, encoder.encode(code), expiresAt));
        return code;
    }

    /** True iff a live, unconsumed, non-exhausted code matches; consumes it on success. */
    @Transactional
    public boolean verify(Long userId, Purpose purpose, String rawCode) {
        OneTimeCode otc = repo
            .findFirstByUserIdAndPurposeAndConsumedFalseOrderByCreatedAtDesc(userId, purpose)
            .orElse(null);
        if (otc == null) return false;
        if (Instant.now().isAfter(otc.getExpiresAt())) return false;
        if (otc.getAttempts() >= props.otpMaxAttempts()) return false;
        if (encoder.matches(rawCode, otc.getCodeHash())) {
            otc.consume();
            repo.save(otc);
            return true;
        }
        otc.incrementAttempts();
        repo.save(otc);
        return false;
    }
}
