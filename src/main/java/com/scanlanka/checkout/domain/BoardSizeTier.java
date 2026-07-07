package com.scanlanka.checkout.domain;

/**
 * Domex courier size band — flat rate per area, not weight (05 delivery-cost-model.md, owner 2026-07-03).
 * Only two pricing tiers exist; boards over 6 ft are charged the {@code BETWEEN_2FT_6FT} rate. Which large
 * boards may not be couriered <b>to outer areas</b> is a separate per-variant flag ({@code courier_outer_blocked}),
 * not a pricing tier. A null tier means the product is not couriable at all (e.g. glass boards — lorry only).
 */
public enum BoardSizeTier {
    UNDER_2FT,
    BETWEEN_2FT_6FT
}
