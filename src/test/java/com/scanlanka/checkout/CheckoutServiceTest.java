package com.scanlanka.checkout;

import com.scanlanka.catalog.app.ProductLookupService;
import com.scanlanka.catalog.app.ProductLookupService.OrderLine;
import com.scanlanka.checkout.app.CheckoutService;
import com.scanlanka.checkout.app.CheckoutService.ItemInput;
import com.scanlanka.checkout.app.CheckoutService.PlaceInput;
import com.scanlanka.checkout.app.CheckoutService.Quote;
import com.scanlanka.checkout.app.DeliveryCostEngine;
import com.scanlanka.checkout.app.StockReservationService;
import com.scanlanka.checkout.infra.DeliveryConfigRepository;
import com.scanlanka.checkout.infra.DeliveryZonePostalCodeRepository;
import com.scanlanka.checkout.infra.DeliveryZoneRepository;
import com.scanlanka.checkout.infra.TaxConfigRepository;
import com.scanlanka.order.app.OrderCommands.CreateOrderCommand;
import com.scanlanka.order.app.OrderCommands.LineSnapshot;
import com.scanlanka.order.app.OrderService;
import com.scanlanka.order.domain.DeliveryPayment;
import com.scanlanka.order.domain.FulfilmentType;
import com.scanlanka.order.domain.Order;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit coverage for the server-authoritative pricing pass (05-checkout). Guards against:
 *  - duplicate (product, variant) inputs double-counting (#5);
 *  - totals and order snapshots diverging because lines are priced more than once (#6).
 */
class CheckoutServiceTest {

    private final ProductLookupService catalog = mock(ProductLookupService.class);
    private final DeliveryZoneRepository zones = mock(DeliveryZoneRepository.class);
    private final DeliveryZonePostalCodeRepository postalCodes = mock(DeliveryZonePostalCodeRepository.class);
    private final DeliveryConfigRepository deliveryConfigs = mock(DeliveryConfigRepository.class);
    private final TaxConfigRepository taxConfigs = mock(TaxConfigRepository.class);
    private final DeliveryCostEngine deliveryEngine = new DeliveryCostEngine();
    private final OrderService orderService = mock(OrderService.class);
    private final StockReservationService reservations = mock(StockReservationService.class);

    private final CheckoutService checkout = new CheckoutService(catalog, zones, postalCodes,
        deliveryConfigs, taxConfigs, deliveryEngine, orderService, reservations);

    private void stubLine(long productId, long unitPriceCents, Integer stock, int available) {
        when(catalog.resolveOrderLine(eq(productId), isNull()))
            .thenReturn(Optional.of(new OrderLine(productId, null, "SKU-" + productId, "Item " + productId,
                null, unitPriceCents, stock)));
        when(reservations.availableQuantity(eq(productId), isNull(), stock == null ? isNull() : eq(stock)))
            .thenReturn(available);
        when(taxConfigs.findById(1)).thenReturn(Optional.empty()); // 0% tax keeps arithmetic obvious
    }

    @Test
    void duplicateLinesForSameProductAreConsolidated() {
        stubLine(1L, 250L, null, Integer.MAX_VALUE);

        Quote q = checkout.quote(List.of(new ItemInput(1L, null, 2), new ItemInput(1L, null, 3)),
            FulfilmentType.PICKUP_SHOP, null, DeliveryPayment.PREPAID);

        assertThat(q.lineCount()).isEqualTo(1);                 // one line, not two
        assertThat(q.subtotalCents()).isEqualTo(250L * 5);      // qty summed to 5, not first-only
        assertThat(q.totalCents()).isEqualTo(250L * 5);
    }

    @Test
    void consolidatedQuantityIsCappedToAvailableStock() {
        stubLine(1L, 250L, 4, 4);

        Quote q = checkout.quote(List.of(new ItemInput(1L, null, 3), new ItemInput(1L, null, 3)),
            FulfilmentType.PICKUP_SHOP, null, DeliveryPayment.PREPAID);

        assertThat(q.lineCount()).isEqualTo(1);
        assertThat(q.subtotalCents()).isEqualTo(250L * 4);      // 6 requested, capped to 4
    }

    @Test
    void placePricesEachLineOnce_andSnapshotMatchesQuoteTotal() {
        stubLine(1L, 250L, null, Integer.MAX_VALUE);
        Order order = mock(Order.class);
        when(order.getId()).thenReturn(10L);
        when(order.getOrderNumber()).thenReturn("SL-TEST-1");
        when(orderService.createDraft(any())).thenReturn(order);

        var placed = checkout.place(new PlaceInput(List.of(new ItemInput(1L, null, 2)),
            FulfilmentType.PICKUP_SHOP, DeliveryPayment.PREPAID, null, null,
            "Buyer", "0770000000", "buyer@example.com", null, "buyer@example.com"));

        assertThat(placed.orderNumber()).isEqualTo("SL-TEST-1");

        // #6: a single resolve/price pass for the whole place() call (was 2 — quote + snapshot build).
        verify(catalog, times(1)).resolveOrderLine(eq(1L), isNull());

        ArgumentCaptor<CreateOrderCommand> cmd = ArgumentCaptor.forClass(CreateOrderCommand.class);
        verify(orderService).createDraft(cmd.capture());
        List<LineSnapshot> lines = cmd.getValue().lines();
        assertThat(lines).hasSize(1);
        assertThat(lines.get(0).quantity()).isEqualTo(2);
        assertThat(lines.get(0).lineTotalCents()).isEqualTo(500L);
        // snapshot line totals reconcile with the order subtotal stored on the command
        assertThat(cmd.getValue().subtotalCents())
            .isEqualTo(lines.stream().mapToLong(LineSnapshot::lineTotalCents).sum());
        verify(reservations).reserveForOrder(eq(10L), any());
    }
}
