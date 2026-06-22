package com.scanlanka.payment.app;

import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * PayHere MD5 hashing (06 FR-PAY-1/2, SEC-PAY-2) per PayHere's spec.
 * - init hash  = upper(md5(merchantId + orderRef + amount + currency + upper(md5(secret))))
 * - notify sig = upper(md5(merchantId + orderRef + amount + currency + statusCode + upper(md5(secret))))
 * The merchant secret never leaves the server. Verification is constant-time.
 */
@Service
public class PayHereHasher {

    private final PayHereProperties props;
    private final String secretHash;

    public PayHereHasher(PayHereProperties props) {
        this.props = props;
        this.secretHash = upperMd5(props.merchantSecret() == null ? "" : props.merchantSecret());
    }

    public String initHash(String orderRef, String amount, String currency) {
        return upperMd5(props.merchantId() + orderRef + amount + currency + secretHash);
    }

    public boolean verifyNotify(String orderRef, String amount, String currency, String statusCode, String md5sig) {
        String local = upperMd5(props.merchantId() + orderRef + amount + currency + statusCode + secretHash);
        return md5sig != null && MessageDigest.isEqual(
            local.getBytes(StandardCharsets.UTF_8), md5sig.getBytes(StandardCharsets.UTF_8));
    }

    private static String upperMd5(String input) {
        try {
            byte[] digest = MessageDigest.getInstance("MD5").digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().withUpperCase().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
