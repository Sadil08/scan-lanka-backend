package com.scanlanka.payment.web;

import com.scanlanka.payment.app.BankTransferService;
import com.scanlanka.payment.app.PaymentMethodsService;
import com.scanlanka.payment.app.PaymentMethodsService.PaymentMethodsView;
import com.scanlanka.payment.app.PaymentService;
import com.scanlanka.payment.app.PaymentService.InitiateResult;
import com.scanlanka.payment.app.PaymentService.NotifyParams;
import com.scanlanka.shared.security.AuthPrincipal;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;

/** Payment endpoints (06 §3). initiate returns signed PayHere params; notify is the gateway webhook. */
@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final PaymentService payments;
    private final BankTransferService bankTransfer;
    private final PaymentMethodsService paymentMethodsService;

    public PaymentController(PaymentService payments, BankTransferService bankTransfer,
                             PaymentMethodsService paymentMethodsService) {
        this.payments = payments;
        this.bankTransfer = bankTransfer;
        this.paymentMethodsService = paymentMethodsService;
    }

    @GetMapping("/methods")
    public PaymentMethodsView methods() {
        return paymentMethodsService.methods();
    }

    public record InitiateRequest(@NotBlank String orderNumber) {}

    @PostMapping("/payhere/initiate")
    public InitiateResult initiate(@RequestBody InitiateRequest req) {
        return payments.initiate(req.orderNumber());
    }

    /** PayHere server-to-server callback (form-encoded). Public; every call is signature-verified. */
    @PostMapping("/payhere/notify")
    public ResponseEntity<Void> notify(
        @RequestParam("merchant_id") String merchantId,
        @RequestParam("order_id") String orderId,
        @RequestParam("payhere_amount") String amount,
        @RequestParam("payhere_currency") String currency,
        @RequestParam("status_code") String statusCode,
        @RequestParam("md5sig") String md5sig,
        @RequestParam(name = "payment_id", required = false) String paymentId) {
        payments.handleNotify(new NotifyParams(merchantId, orderId, amount, currency, statusCode, md5sig, paymentId));
        return ResponseEntity.ok().build();
    }

    /**
     * Customer uploads a bank-transfer slip for their order (06 FR-PAY-6).
     * Ownership-guarded (T-1b): a logged-in user may only upload for their own order; a guest must
     * present the email the order was placed with. Prevents queue pollution / submission-blocking DoS.
     */
    @PostMapping(value = "/bank-transfer/slip", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Void> uploadSlip(@RequestParam("orderNumber") String orderNumber,
                                           @RequestParam("file") MultipartFile file,
                                           @RequestParam(name = "email", required = false) String email,
                                           @AuthenticationPrincipal AuthPrincipal principal) {
        try {
            Long userId = principal != null ? principal.userId() : null;
            bankTransfer.uploadSlip(orderNumber, file.getBytes(), userId, email);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }
}
