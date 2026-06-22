package com.scanlanka.catalog.app;

import com.scanlanka.catalog.domain.Product;
import com.scanlanka.catalog.domain.PriceMode;
import com.scanlanka.catalog.infra.ProductRepository;
import com.scanlanka.order.infra.OrderItemRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductServiceDeleteTest {

    @Mock ProductRepository products;
    @Mock OrderItemRepository orderItems;
    @Mock CatalogCacheEvictor cacheEvictor;
    @Mock com.scanlanka.catalog.infra.SpecGroupRepository groups;
    @Mock com.scanlanka.catalog.infra.SpecOptionRepository options;
    @Mock com.scanlanka.catalog.infra.ProductVariantRepository variants;
    @Mock VariantService variantService;
    @Mock ProductPricingService pricing;

    @InjectMocks ProductService productService;

    @Test
    void deleteHardDeletesWhenNeverOrdered() {
        Product p = new Product("X", "x", "SKU1", PriceMode.SINGLE);
        when(products.findById(1L)).thenReturn(Optional.of(p));
        when(orderItems.existsByProductId(1L)).thenReturn(false);

        assertThat(productService.delete(1L)).isEqualTo("DELETED");
        verify(products).delete(p);
        verify(cacheEvictor).evictAll();
    }

    @Test
    void deleteArchivesWhenOrdered() {
        Product p = new Product("X", "x", "SKU1", PriceMode.SINGLE);
        when(products.findById(1L)).thenReturn(Optional.of(p));
        when(orderItems.existsByProductId(1L)).thenReturn(true);

        assertThat(productService.delete(1L)).isEqualTo("ARCHIVED");
        verify(products, never()).delete(any());
        verify(products).save(p);
        assertThat(p.isArchived()).isTrue();
        verify(cacheEvictor).evictAll();
    }

    @Test
    void deleteNotFound() {
        when(products.findById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> productService.delete(99L)).isInstanceOf(ResponseStatusException.class);
    }
}
