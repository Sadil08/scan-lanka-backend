package com.scanlanka.order.domain;

/** How the order is received (05/09). Delivery = local ground transport only. */
public enum FulfilmentType {
    DELIVERY,
    PICKUP_SHOP,
    PICKUP_FACTORY
}
