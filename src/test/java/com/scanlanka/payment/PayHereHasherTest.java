package com.scanlanka.payment;

import com.scanlanka.payment.app.PayHereHasher;
import com.scanlanka.payment.app.PayHereProperties;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;

class PayHereHasherTest {

    private static final String MERCHANT_ID = "1226000";
    private static final String SECRET = "test-merchant-secret";

    private final PayHereProperties props = new PayHereProperties(
        MERCHANT_ID, SECRET, "LKR",
        "https://sandbox.payhere.lk/pay/checkout", "https://x/notify", "https://x/return", "https://x/cancel");
    private final PayHereHasher hasher = new PayHereHasher(props);

    @Test
    void initHashIsDeterministicUppercaseHex() {
        assertThat(hasher.initHash("SL-1", "505.00", "LKR"))
            .isEqualTo(hasher.initHash("SL-1", "505.00", "LKR"))
            .matches("[0-9A-F]{32}");
    }

    @Test
    void verifyAcceptsGenuineSignature() {
        String sig = gatewaySig(SECRET, "SL-1", "505.00", "LKR", "2");
        assertThat(hasher.verifyNotify("SL-1", "505.00", "LKR", "2", sig)).isTrue();
    }

    @Test
    void verifyRejectsTamperedAmountAndBadSig() {
        String sig = gatewaySig(SECRET, "SL-1", "505.00", "LKR", "2");
        assertThat(hasher.verifyNotify("SL-1", "1.00", "LKR", "2", sig)).isFalse();  // amount tampered
        assertThat(hasher.verifyNotify("SL-1", "505.00", "LKR", "2", "DEADBEEF")).isFalse();
    }

    @Test
    void verifyRejectsForgedWithWrongSecret() {
        String forged = gatewaySig("WRONG-secret", "SL-1", "505.00", "LKR", "2");
        assertThat(hasher.verifyNotify("SL-1", "505.00", "LKR", "2", forged)).isFalse();
    }

    /** Simulates PayHere's notify-sig computation (the gateway side). */
    private static String gatewaySig(String secret, String orderRef, String amount, String currency, String status) {
        String secretHash = md5Upper(secret);
        return md5Upper(MERCHANT_ID + orderRef + amount + currency + status + secretHash);
    }

    private static String md5Upper(String s) {
        try {
            return HexFormat.of().withUpperCase()
                .formatHex(MessageDigest.getInstance("MD5").digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
