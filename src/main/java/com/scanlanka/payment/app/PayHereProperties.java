package com.scanlanka.payment.app;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** PayHere config (06). Merchant id/secret from env/secret manager (global/02 §5, SEC-PAY-2). */
@ConfigurationProperties(prefix = "app.payhere")
public record PayHereProperties(
    String merchantId,
    String merchantSecret,
    @DefaultValue("LKR") String currency,
    @DefaultValue("https://sandbox.payhere.lk/pay/checkout") String checkoutUrl,
    String notifyUrl,
    String returnUrl,
    String cancelUrl
) {
}
