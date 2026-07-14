package com.scanlanka.catalog.app;

import com.scanlanka.catalog.domain.SpecOption;
import com.scanlanka.catalog.infra.SpecOptionRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Guards the owner 2026-07-14 fix: an order/quote line's snapshotted name must carry the
 * chosen variant's size (e.g. "Scan White Board (4 x 3)"), not just the bare product name -
 * admin and customers otherwise can't tell which size was actually ordered (09 FR-ORDER-17).
 */
class VariantLabelResolverTest {

    private final SpecOptionRepository options = mock(SpecOptionRepository.class);
    private final VariantLabelResolver resolver = new VariantLabelResolver(options);

    @Test
    void sizeLabel_resolvesSingleOption() {
        SpecOption size = mock(SpecOption.class);
        when(size.getValue()).thenReturn("4 x 3");
        when(options.findById(42L)).thenReturn(Optional.of(size));

        assertThat(resolver.sizeLabel("42")).isEqualTo("4 x 3");
    }

    @Test
    void sizeLabel_joinsMultipleOptions() {
        SpecOption size = mock(SpecOption.class);
        when(size.getValue()).thenReturn("4 x 3");
        SpecOption color = mock(SpecOption.class);
        when(color.getValue()).thenReturn("Green");
        when(options.findById(1L)).thenReturn(Optional.of(size));
        when(options.findById(2L)).thenReturn(Optional.of(color));

        assertThat(resolver.sizeLabel("1,2")).isEqualTo("4 x 3 / Green");
    }

    @Test
    void sizeLabel_nullForBlankOrUnresolvedSignature() {
        assertThat(resolver.sizeLabel(null)).isNull();
        assertThat(resolver.sizeLabel("")).isNull();
        when(options.findById(999L)).thenReturn(Optional.empty());
        assertThat(resolver.sizeLabel("999")).isNull();
    }

    @Test
    void nameWithSize_appendsResolvedSize() {
        SpecOption size = mock(SpecOption.class);
        when(size.getValue()).thenReturn("4 x 3");
        when(options.findById(7L)).thenReturn(Optional.of(size));

        assertThat(resolver.nameWithSize("Scan White Board", "7")).isEqualTo("Scan White Board (4 x 3)");
    }

    @Test
    void nameWithSize_fallsBackToBareNameForSinglePricedProducts() {
        assertThat(resolver.nameWithSize("Scan Canvas Board", null)).isEqualTo("Scan Canvas Board");
    }
}
