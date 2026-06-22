package com.scanlanka.auth.infra;

import com.scanlanka.auth.domain.OneTimeCode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OneTimeCodeRepository extends JpaRepository<OneTimeCode, Long> {

    Optional<OneTimeCode> findFirstByUserIdAndPurposeAndConsumedFalseOrderByCreatedAtDesc(
        Long userId, OneTimeCode.Purpose purpose);
}
