package com.scanlanka.checkout;

import com.scanlanka.catalog.domain.Product;
import com.scanlanka.catalog.domain.ProductVariant;
import com.scanlanka.catalog.infra.ProductRepository;
import com.scanlanka.catalog.infra.ProductVariantRepository;
import com.scanlanka.checkout.app.StockReservationService;
import com.scanlanka.checkout.infra.StockReservationRepository;
import com.scanlanka.order.app.OrderCommands.LineSnapshot;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * A null {@code stockQty} means "unlimited" (01 FR-CATALOG-9). Regression test for a bug where
 * {@code Optional.map(Entity::getStockQty).orElse(0)} silently turned "unlimited" into "zero stock"
 * (Optional.map treats a null mapper result as absence), rejecting every order for every
 * unlimited-stock product with a false STOCK_EXCEEDED — and a follow-up ternary-unboxing NPE from the
 * naive fix ({@code cond ? 0 : someInteger} force-unboxes the Integer branch).
 */
class StockReservationServiceTest {

    private final StockReservationRepository reservations = mock(StockReservationRepository.class);
    private final ProductRepository products = mock(ProductRepository.class);
    private final ProductVariantRepository variants = mock(ProductVariantRepository.class);

    private final StockReservationService service =
        new StockReservationService(reservations, products, variants);

    @Test
    void unlimitedStockVariantIsAlwaysAvailable() {
        assertThat(service.availableQuantity(1L, 10L, null)).isEqualTo(Integer.MAX_VALUE);
    }

    @Test
    void reservingAgainstAnUnlimitedStockVariantNeverThrows() {
        ProductVariant v = mock(ProductVariant.class);
        when(v.getProductId()).thenReturn(1L);
        when(v.getStockQty()).thenReturn(null); // unlimited
        when(variants.findById(10L)).thenReturn(Optional.of(v));
        lenient().when(products.findByIdForUpdate(1L)).thenReturn(Optional.of(mock(Product.class)));

        assertThatCode(() -> service.reserveForOrder(99L,
            List.of(new LineSnapshot(1L, 10L, "SKU", "Item", "STANDARD", 10000, 5, 50000))))
            .doesNotThrowAnyException();
    }

    @Test
    void reservingAgainstALimitedStockVariantRespectsTheCap() {
        ProductVariant v = mock(ProductVariant.class);
        when(v.getProductId()).thenReturn(1L);
        when(v.getStockQty()).thenReturn(2);
        when(variants.findById(10L)).thenReturn(Optional.of(v));
        when(products.findByIdForUpdate(1L)).thenReturn(Optional.of(mock(Product.class)));
        when(reservations.sumActiveQuantity(anyLong(), anyLong(), any())).thenReturn(0);

        assertThatCode(() -> service.reserveForOrder(99L,
            List.of(new LineSnapshot(1L, 10L, "SKU", "Item", "STANDARD", 10000, 3, 30000))))
            .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
            .hasMessageContaining("STOCK_EXCEEDED");
    }

    @Test
    void unlimitedStockProductWithNoVariantIsAlwaysAvailable() {
        Product p = mock(Product.class);
        when(p.getStockQty()).thenReturn(null);
        when(products.findById(1L)).thenReturn(Optional.of(p));
        lenient().when(products.findByIdForUpdate(1L)).thenReturn(Optional.of(p));

        assertThatCode(() -> service.reserveForOrder(99L,
            List.of(new LineSnapshot(1L, null, "SKU", "Item", "STANDARD", 10000, 4, 40000))))
            .doesNotThrowAnyException();
    }
}
