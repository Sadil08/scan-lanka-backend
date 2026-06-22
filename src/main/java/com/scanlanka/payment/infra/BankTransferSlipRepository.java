package com.scanlanka.payment.infra;

import com.scanlanka.payment.domain.BankTransferSlip;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BankTransferSlipRepository extends JpaRepository<BankTransferSlip, Long> {
    Optional<BankTransferSlip> findFirstByPaymentIdOrderByUploadedAtDesc(Long paymentId);
    boolean existsBySlipHash(String slipHash);
}
