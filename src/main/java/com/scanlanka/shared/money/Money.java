package com.scanlanka.shared.money;

/**
 * Money is always integer minor units (cents) in LKR (global/03 §3, global/09 §4).
 * No floating point. Display formatting (whole rupees) is a frontend concern; currency display
 * conversion for international visitors is display-only (geo `13`) — the charged amount is LKR.
 */
public final class Money {

    public static final String CURRENCY = "LKR";

    private Money() {}

    /** Half-up rounding on a total (not per line) to avoid drift (global/05 BA-34 / 05-checkout FR-20). */
    public static long roundHalfUp(double cents) {
        return Math.round(cents);
    }

    public static long requireNonNegative(long cents) {
        if (cents < 0) throw new IllegalArgumentException("amount must be >= 0");
        return cents;
    }
}
