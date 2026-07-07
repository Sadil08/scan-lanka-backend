package com.scanlanka.checkout.domain;

/**
 * How the customer pays for a {@code COMPANY_LORRY} order (06 FR-PAY-13/16, owner 2026-07-03).
 * {@code ONLINE} = product + lorry charge prepaid via PayHere card or bank transfer; {@code COD} =
 * driver collects the full amount in cash on delivery. Courier orders are always full COD regardless
 * of this choice (an explicit {@code ONLINE} on a courier order is rejected — SEC-DELIV-1).
 */
public enum PaymentChoice {
    ONLINE,
    COD
}
