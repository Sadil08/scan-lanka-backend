package com.scanlanka.checkout;

import com.scanlanka.catalog.app.ProductLookupService;
import com.scanlanka.catalog.app.ProductLookupService.OrderLine;
import com.scanlanka.checkout.app.CheckoutService;
import com.scanlanka.checkout.app.CheckoutService.ItemInput;
import com.scanlanka.checkout.app.CheckoutService.PlaceInput;
import com.scanlanka.checkout.app.CheckoutService.Quote;
import com.scanlanka.checkout.app.DeliveryOptionsService;
import com.scanlanka.checkout.app.DeliveryOptionsService.DeliveryQuote;
import com.scanlanka.checkout.app.DeliveryOptionsService.Option;
import com.scanlanka.checkout.app.StockReservationService;
import com.scanlanka.checkout.domain.DeliveryMethod;
import com.scanlanka.checkout.infra.PayHereFeeConfigRepository;
import com.scanlanka.checkout.infra.TaxConfigRepository;
import com.scanlanka.order.app.OrderCommands.CreateOrderCommand;
import com.scanlanka.order.app.OrderCommands.LineSnapshot;
import com.scanlanka.order.app.OrderService;
import com.scanlanka.order.domain.Order;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
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
 * Delivery is stubbed to a free, available lorry rail so the arithmetic stays on the line totals.
 */
class CheckoutServiceTest {

    private final ProductLookupService catalog = mock(ProductLookupService.class);
    private final TaxConfigRepository taxConfigs = mock(TaxConfigRepository.class);
    private final PayHereFeeConfigRepository payHereFeeConfigs = mock(PayHereFeeConfigRepository.class);
    private final DeliveryOptionsService deliveryOptions = mock(DeliveryOptionsService.class);
    private final OrderService orderService = mock(OrderService.class);
    private final StockReservationService reservations = mock(StockReservationService.class);

    private final CheckoutService checkout = new CheckoutService(catalog, taxConfigs, payHereFeeConfigs,
        deliveryOptions, orderService, reservations,
        mock(org.springframework.context.ApplicationEventPublisher.class));

    @BeforeEach
    void freeLorryRail() {
        when(taxConfigs.findFirstByOrderByIdAsc()).thenReturn(Optional.empty()); // 0% tax → obvious arithmetic
        when(payHereFeeConfigs.findFirstByOrderByIdAsc()).thenReturn(Optional.empty()); // 0% fee by default
        DeliveryQuote dq = new DeliveryQuote(false, true, "COLOMBO",
            List.of(new Option(DeliveryMethod.COMPANY_LORRY, true, null, 0, 0, false, 0, List.of())));
        when(deliveryOptions.options(any(), any(), any(), anyLong())).thenReturn(dq);
    }

    private void stubLine(long productId, long unitPriceCents, Integer stock, int available) {
        when(catalog.resolveOrderLine(eq(productId), isNull()))
            .thenReturn(Optional.of(new OrderLine(productId, null, "SKU-" + productId, "Item " + productId,
                null, unitPriceCents, stock, null, null, null, null, null, null, null, null,
                true, true, true, false, false, true, false)));
        when(reservations.availableQuantity(eq(productId), isNull(), stock == null ? isNull() : eq(stock)))
            .thenReturn(available);
    }

    @Test
    void duplicateLinesForSameProductAreConsolidated() {
        stubLine(1L, 250L, null, Integer.MAX_VALUE);

        Quote q = checkout.quote(List.of(new ItemInput(1L, null, 2), new ItemInput(1L, null, 3)),
            DeliveryMethod.COMPANY_LORRY, "00100", null, null);

        assertThat(q.lineCount()).isEqualTo(1);                 // one line, not two
        assertThat(q.subtotalCents()).isEqualTo(250L * 5);      // qty summed to 5, not first-only
        assertThat(q.onlineTotalCents()).isEqualTo(250L * 5);   // free delivery + 0 tax
    }

    @Test
    void consolidatedQuantityIsCappedToAvailableStock() {
        stubLine(1L, 250L, 4, 4);

        Quote q = checkout.quote(List.of(new ItemInput(1L, null, 3), new ItemInput(1L, null, 3)),
            DeliveryMethod.COMPANY_LORRY, "00100", null, null);

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
            DeliveryMethod.COMPANY_LORRY, null /* paymentChoice ⇒ ONLINE */, null, null,
            "Buyer", "0770000000", "buyer@example.com", null, "buyer@example.com", "CARD"));

        assertThat(placed.orderNumber()).isEqualTo("SL-TEST-1");

        // #6: a single resolve/price pass for the whole place() call (was 2 — quote + snapshot build).
        verify(catalog, times(1)).resolveOrderLine(eq(1L), isNull());

        ArgumentCaptor<CreateOrderCommand> cmd = ArgumentCaptor.forClass(CreateOrderCommand.class);
        verify(orderService).createDraft(cmd.capture());
        List<LineSnapshot> lines = cmd.getValue().lines();
        assertThat(lines).hasSize(1);
        assertThat(lines.get(0).quantity()).isEqualTo(2);
        assertThat(lines.get(0).lineTotalCents()).isEqualTo(500L);
        assertThat(cmd.getValue().deliveryMethod()).isEqualTo("COMPANY_LORRY");
        // snapshot line totals reconcile with the order subtotal stored on the command
        assertThat(cmd.getValue().subtotalCents())
            .isEqualTo(lines.stream().mapToLong(LineSnapshot::lineTotalCents).sum());
        assertThat(cmd.getValue().payhereFeeCents()).isEqualTo(0L); // 0% PayHere fee by default
        verify(reservations).reserveForOrder(eq(10L), any());
    }

    @Test
    void cardPaymentAddsPayHereFeeOnTopOfDoorTotal_bankAndCodDoNot() {
        stubLine(1L, 250L, null, Integer.MAX_VALUE);
        com.scanlanka.checkout.domain.PayHereFeeConfig feeConfig =
            mock(com.scanlanka.checkout.domain.PayHereFeeConfig.class);
        when(feeConfig.getRateBps()).thenReturn(500); // 5%
        when(payHereFeeConfigs.findFirstByOrderByIdAsc()).thenReturn(Optional.of(feeConfig));
        Order order = mock(Order.class);
        when(order.getId()).thenReturn(11L);
        when(order.getOrderNumber()).thenReturn("SL-TEST-2");
        when(orderService.createDraft(any())).thenReturn(order);

        // subtotal = 250*2 = 500, free delivery + 0% tax ⇒ door total 500 ⇒ 5% fee = 25.
        checkout.place(new PlaceInput(List.of(new ItemInput(1L, null, 2)),
            DeliveryMethod.COMPANY_LORRY, null, null, null,
            "Buyer", "0770000000", "buyer@example.com", null, "buyer@example.com", "CARD"));

        ArgumentCaptor<CreateOrderCommand> cmd = ArgumentCaptor.forClass(CreateOrderCommand.class);
        verify(orderService).createDraft(cmd.capture());
        assertThat(cmd.getValue().payhereFeeCents()).isEqualTo(25L);
        assertThat(cmd.getValue().totalCents()).isEqualTo(525L);

        // Bank transfer: same door total, no PayHere fee.
        checkout.place(new PlaceInput(List.of(new ItemInput(1L, null, 2)),
            DeliveryMethod.COMPANY_LORRY, null, null, null,
            "Buyer", "0770000000", "buyer@example.com", null, "buyer@example.com", "BANK"));
        ArgumentCaptor<CreateOrderCommand> bankCmd = ArgumentCaptor.forClass(CreateOrderCommand.class);
        verify(orderService, times(2)).createDraft(bankCmd.capture());
        assertThat(bankCmd.getValue().payhereFeeCents()).isEqualTo(0L);
        assertThat(bankCmd.getValue().totalCents()).isEqualTo(500L);

        // Cash on delivery: never charged online, never a PayHere fee.
        checkout.place(new PlaceInput(List.of(new ItemInput(1L, null, 2)),
            DeliveryMethod.COMPANY_LORRY, com.scanlanka.checkout.domain.PaymentChoice.COD, null, null,
            "Buyer", "0770000000", "buyer@example.com", null, "buyer@example.com", "CARD"));
        ArgumentCaptor<CreateOrderCommand> codCmd = ArgumentCaptor.forClass(CreateOrderCommand.class);
        verify(orderService, times(3)).createDraft(codCmd.capture());
        assertThat(codCmd.getValue().payhereFeeCents()).isEqualTo(0L);
        assertThat(codCmd.getValue().totalCents()).isEqualTo(0L);
        assertThat(codCmd.getValue().deliveryCodCents()).isEqualTo(500L); // full door total collected in cash
    }
}
