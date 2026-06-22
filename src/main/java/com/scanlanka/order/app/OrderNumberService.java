package com.scanlanka.order.app;

import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Verifiable order numbers (09 FR-ORDER-9): SL-YYYYMMDD-<rand>-<sig>, sig = HMAC-SHA256(secret,
 * date+rand) truncated. Date-encoded (Asia/Colombo), non-sequential, server-verifiable, unforgeable.
 */
@Service
public class OrderNumberService {

    private static final ZoneId COLOMBO = ZoneId.of("Asia/Colombo");
    private static final DateTimeFormatter DATE = DateTimeFormatter.BASIC_ISO_DATE; // yyyyMMdd
    private static final Pattern PATTERN = Pattern.compile("SL-(\\d{8})-([0-9A-Z]+)-([0-9A-F]{8})");
    private static final SecureRandom RNG = new SecureRandom();

    private final byte[] secret;

    public OrderNumberService(OrderProperties props) {
        this.secret = props.signingSecret().getBytes(StandardCharsets.UTF_8);
    }

    public String generate() {
        String date = LocalDate.now(COLOMBO).format(DATE);
        String rand = Long.toString(Math.abs(RNG.nextLong()), 36).toUpperCase(Locale.ROOT);
        rand = rand.length() > 8 ? rand.substring(0, 8) : rand;
        return "SL-" + date + "-" + rand + "-" + sign(date + rand);
    }

    public boolean verify(String orderNumber) {
        if (orderNumber == null) return false;
        Matcher m = PATTERN.matcher(orderNumber);
        if (!m.matches()) return false;
        String expected = sign(m.group(1) + m.group(2));
        return MessageDigest.isEqual(
            expected.getBytes(StandardCharsets.UTF_8), m.group(3).getBytes(StandardCharsets.UTF_8));
    }

    private String sign(String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            byte[] full = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().withUpperCase().formatHex(full).substring(0, 8);
        } catch (Exception e) {
            throw new IllegalStateException("order number signing failed", e);
        }
    }
}
