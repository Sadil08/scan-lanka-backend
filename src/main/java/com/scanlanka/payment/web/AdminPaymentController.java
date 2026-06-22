package com.scanlanka.payment.web;

import com.scanlanka.payment.app.BankTransferService;
import com.scanlanka.shared.security.AuthPrincipal;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Admin bank-payment review (06 FR-PAY-6). Under /api/admin/** → ADMIN-gated. Manual confirm only (T-1b). */
@RestController
@RequestMapping("/api/admin/payments")
public class AdminPaymentController {

    private final BankTransferService bankTransfer;

    public AdminPaymentController(BankTransferService bankTransfer) {
        this.bankTransfer = bankTransfer;
    }

    public record ReviewRequest(String note) {}

    @PostMapping("/{orderNumber}/bank-confirm")
    public ResponseEntity<Void> confirm(@PathVariable String orderNumber,
                                        @RequestBody(required = false) ReviewRequest req,
                                        @AuthenticationPrincipal AuthPrincipal admin) {
        bankTransfer.confirm(orderNumber, admin != null ? admin.userId() : null, note(req));
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{orderNumber}/bank-reject")
    public ResponseEntity<Void> reject(@PathVariable String orderNumber,
                                       @RequestBody(required = false) ReviewRequest req,
                                       @AuthenticationPrincipal AuthPrincipal admin) {
        bankTransfer.reject(orderNumber, admin != null ? admin.userId() : null, note(req));
        return ResponseEntity.ok().build();
    }

    private static String note(ReviewRequest req) {
        return req != null ? req.note() : null;
    }
}
