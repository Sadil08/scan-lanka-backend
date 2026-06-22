package com.scanlanka.payment.web;

import com.scanlanka.payment.app.BankTransferService;
import com.scanlanka.payment.app.PaymentService;
import com.scanlanka.payment.app.PaymentService.InitiateResult;
import com.scanlanka.payment.app.PaymentService.NotifyParams;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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

    public PaymentController(PaymentService payments, BankTransferService bankTransfer) {
        this.payments = payments;
        this.bankTransfer = bankTransfer;
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
        payments.handleNotify(new NotifyParams(orderId, amount, currency, statusCode, md5sig, paymentId));
        return ResponseEntity.ok().build();
    }

    /** Customer uploads a bank-transfer slip for their order (06 FR-PAY-6). */
    @PostMapping(value = "/bank-transfer/slip", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Void> uploadSlip(@RequestParam("orderNumber") String orderNumber,
                                           @RequestParam("file") MultipartFile file) {
        try {
            bankTransfer.uploadSlip(orderNumber, file.getBytes());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }
}
