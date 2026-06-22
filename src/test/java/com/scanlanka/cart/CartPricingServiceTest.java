package com.scanlanka.cart;

import com.scanlanka.cart.app.CartPricingService;
import com.scanlanka.cart.app.CartPricingService.LineInput;
import com.scanlanka.cart.app.CartPricingService.PricedCart;
import com.scanlanka.catalog.app.ProductLookupService;
import com.scanlanka.catalog.app.ProductLookupService.LinePricing;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CartPricingServiceTest {

    private final ProductLookupService lookup = mock(ProductLookupService.class);
    private final CartPricingService pricing = new CartPricingService(lookup);

    @Test
    void pricesLinesServerSideAndSumsSubtotal() {
        when(lookup.resolveLine(1L, null)).thenReturn(Optional.of(new LinePricing("Marker", 250L, 100)));
        when(lookup.resolveLine(2L, 9L)).thenReturn(Optional.of(new LinePricing("Board", 12000L, null)));

        PricedCart cart = pricing.price(List.of(new LineInput(1L, null, 4), new LineInput(2L, 9L, 1)));

        assertThat(cart.subtotalCents()).isEqualTo(250L * 4 + 12000L);
        assertThat(cart.lines()).allMatch(l -> l.status().equals("OK"));
    }

    @Test
    void capsQuantityToStock() {
        when(lookup.resolveLine(1L, null)).thenReturn(Optional.of(new LinePricing("Marker", 250L, 3)));
        PricedCart cart = pricing.price(List.of(new LineInput(1L, null, 10)));
        assertThat(cart.lines().get(0).quantity()).isEqualTo(3);
        assertThat(cart.lines().get(0).status()).isEqualTo("CAPPED");
        assertThat(cart.subtotalCents()).isEqualTo(750L);
    }

    @Test
    void unavailableLineExcludedFromSubtotal() {
        when(lookup.resolveLine(99L, null)).thenReturn(Optional.empty());
        PricedCart cart = pricing.price(List.of(new LineInput(99L, null, 1)));
        assertThat(cart.lines().get(0).status()).isEqualTo("UNAVAILABLE");
        assertThat(cart.subtotalCents()).isZero();
    }

    @Test
    void clientPriceIsIrrelevant_onlyIdsAndQtyUsed() {
        // LineInput has no price field — the client cannot influence pricing (SEC-CART-2).
        when(lookup.resolveLine(1L, null)).thenReturn(Optional.of(new LinePricing("Marker", 250L, null)));
        PricedCart cart = pricing.price(List.of(new LineInput(1L, null, 2)));
        assertThat(cart.subtotalCents()).isEqualTo(500L);
    }
}
