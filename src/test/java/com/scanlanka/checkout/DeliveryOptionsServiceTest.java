package com.scanlanka.checkout;

import com.scanlanka.checkout.app.CourierEstimateEngine;
import com.scanlanka.checkout.app.CourierZoneResolver;
import com.scanlanka.checkout.app.DeliveryOptionsService;
import com.scanlanka.checkout.app.DeliveryOptionsService.CartLine;
import com.scanlanka.checkout.app.DeliveryOptionsService.DeliveryQuote;
import com.scanlanka.checkout.app.DeliveryOptionsService.Option;
import com.scanlanka.checkout.app.LorryCostEngine;
import com.scanlanka.checkout.domain.BoardSizeTier;
import com.scanlanka.checkout.domain.CourierRateCard;
import com.scanlanka.checkout.domain.CourierZone;
import com.scanlanka.checkout.domain.DeliveryMethod;
import com.scanlanka.checkout.domain.DeliverySettings;
import com.scanlanka.checkout.domain.LorryZone;
import com.scanlanka.checkout.domain.PostalZone;
import com.scanlanka.checkout.infra.CourierRateCardRepository;
import com.scanlanka.checkout.infra.DeliveryMethodConfigRepository;
import com.scanlanka.checkout.infra.DeliverySettingsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Two-rail model (owner 2026-07-04/05, courier re-priced 2026-07-16): the Domex courier is
 * weight-based per board (first kg + additional kgs, plus a per-package handling fee above 2 ft)
 * and per-item switchable (V48); large boards blocked TO outer areas only; fully admin-controlled
 * lorry cells — per-zone enable switch, per-cell gates, flat gate-met charge per zone.
 */
class DeliveryOptionsServiceTest {

    private final CourierRateCardRepository courierRates = mock(CourierRateCardRepository.class);
    private final DeliveryMethodConfigRepository methodConfig = mock(DeliveryMethodConfigRepository.class);
    private final DeliverySettingsRepository settings = mock(DeliverySettingsRepository.class);
    private final CourierZoneResolver zoneResolver = mock(CourierZoneResolver.class);

    private final DeliveryOptionsService service = new DeliveryOptionsService(
        courierRates, methodConfig, settings, new LorryCostEngine(), new CourierEstimateEngine(), zoneResolver);

    @BeforeEach
    void defaults() {
        lenient().when(methodConfig.findById(any())).thenReturn(Optional.empty());
        DeliverySettings s = mock(DeliverySettings.class);
        lenient().when(s.getLorryCapColomboCents()).thenReturn(100000L);  // Rs 1,000 - always applies
        lenient().when(s.getLorryCapSuburbCents()).thenReturn(150000L);   // Rs 1,500 - always applies
        lenient().when(s.getGateMetOuterCents()).thenReturn(100000L);     // Rs 1,000 (admin-set, no cap role)
        lenient().when(settings.findFirstByOrderByIdAsc()).thenReturn(Optional.of(s));
        // Owner 2026-07-16 rate card (V48): weight rates + above-2ft handling fee per area.
        courierRate(CourierZone.CITY_LIMITS, 38000, 18000, 75000);
        courierRate(CourierZone.SUBURBS, 38000, 18000, 100000);
        courierRate(CourierZone.OUTSTATION, 50000, 20000, 150000);
        courierRate(CourierZone.FARAWAY, 65000, 25000, 200000);
    }

    private void courierRate(CourierZone zone, long firstKg, long addlKg, long handling) {
        lenient().when(courierRates.findById(zone))
            .thenReturn(Optional.of(new CourierRateCard(zone, firstKg, addlKg, handling)));
    }

    private void colomboPostal() {
        PostalZone zone = new PostalZone("00100", LorryZone.COLOMBO, CourierZone.CITY_LIMITS, "Colombo", "Western Province");
        when(zoneResolver.postalZone("00100")).thenReturn(Optional.of(zone));
        lenient().when(zoneResolver.courierZone(eq("00100"), any())).thenReturn(Optional.of(CourierZone.CITY_LIMITS));
    }

    private void outerPostal() {
        PostalZone zone = new PostalZone("20000", LorryZone.OUTER, CourierZone.OUTSTATION, "Kandy", "Central Province");
        when(zoneResolver.postalZone("20000")).thenReturn(Optional.of(zone));
        when(zoneResolver.courierZone(eq("20000"), any())).thenReturn(Optional.of(CourierZone.OUTSTATION));
    }

    /** All zones enabled, no gates/flags, courier on, 1 kg — the common happy-path line. */
    private static CartLine board(long lorryCentsAllZones, BoardSizeTier tier, int qty) {
        return board(lorryCentsAllZones, tier, BigDecimal.ONE, qty);
    }

    private static CartLine board(long lorryCentsAllZones, BoardSizeTier tier, BigDecimal weightKg, int qty) {
        return new CartLine("Board", tier, weightKg,
            lorryCentsAllZones, lorryCentsAllZones, lorryCentsAllZones,
            null, null, null, true, true, true, false, false, true, false, qty);
    }

    private static Option optionFor(DeliveryQuote q, DeliveryMethod method) {
        return q.options().stream().filter(o -> o.method() == method).findFirst().orElseThrow();
    }

    // --- courier weight pricing (owner 2026-07-16) ---

    @Test
    void courierChargesFirstKgPlusHandlingInCityLimits() {
        colomboPostal();
        DeliveryQuote q = service.options(List.of(board(80000, BoardSizeTier.BETWEEN_2FT_6FT, 1)), "00100", null, 700000);
        Option courier = optionFor(q, DeliveryMethod.COURIER);
        assertThat(courier.available()).isTrue();
        // 1 kg = Rs 380 + Rs 750 handling (above 2 ft, CITY_LIMITS)
        assertThat(courier.courierEstimateCents()).isEqualTo(113000);
    }

    @Test
    void courierChargesAdditionalKgsWithoutHandlingUnder2ft() {
        colomboPostal();
        DeliveryQuote q = service.options(
            List.of(board(80000, BoardSizeTier.UNDER_2FT, new BigDecimal("3"), 1)), "00100", null, 700000);
        // 3 kg under 2 ft: Rs 380 + 2 × Rs 180, no handling
        assertThat(optionFor(q, DeliveryMethod.COURIER).courierEstimateCents()).isEqualTo(74000);
    }

    @Test
    void courierOwnersWorkedExampleFiveByFourMagneticBoardFaraway() {
        // owner's example: 5 x 4 magnetic board, 20 kg, above 2 ft, faraway:
        // 1st kg Rs 650 + 19 × Rs 250 + Rs 2,000 handling = Rs 7,400
        when(zoneResolver.postalZone("99999")).thenReturn(Optional.empty());
        when(zoneResolver.courierZone(eq("99999"), any())).thenReturn(Optional.of(CourierZone.FARAWAY));
        DeliveryQuote q = service.options(
            List.of(board(80000, BoardSizeTier.BETWEEN_2FT_6FT, new BigDecimal("20"), 1)), "99999", "Madulkele", 700000);
        assertThat(optionFor(q, DeliveryMethod.COURIER).courierEstimateCents()).isEqualTo(740000);
    }

    @Test
    void courierEstimateMultipliesPerBoard() {
        outerPostal();
        DeliveryQuote q = service.options(
            List.of(board(80000, BoardSizeTier.BETWEEN_2FT_6FT, new BigDecimal("2"), 2)), "20000", null, 700000);
        // per board OUTSTATION: Rs 500 + 1 × Rs 200 + Rs 1,500 handling = Rs 2,200; × 2 boards
        assertThat(optionFor(q, DeliveryMethod.COURIER).courierEstimateCents()).isEqualTo(440000);
    }

    @Test
    void courierMissingWeightBillsFirstKgOnly() {
        colomboPostal();
        DeliveryQuote q = service.options(
            List.of(board(80000, BoardSizeTier.UNDER_2FT, null, 1)), "00100", null, 700000);
        assertThat(optionFor(q, DeliveryMethod.COURIER).courierEstimateCents()).isEqualTo(38000);
    }

    // --- per-item courier switch (V48/V49) ---

    @Test
    void courierSwitchedOffItemHidesCourierAndListsItems() {
        colomboPostal();
        CartLine lorryOnly = new CartLine("Scan Green Board 2 x 3", BoardSizeTier.BETWEEN_2FT_6FT, BigDecimal.ONE,
            60000L, 60000L, null, null, null, null, true, true, true,
            false, false, false /* courier OFF */, false, 1);
        DeliveryQuote q = service.options(List.of(lorryOnly), "00100", null, 700000);
        Option courier = optionFor(q, DeliveryMethod.COURIER);
        assertThat(courier.available()).isFalse();
        assertThat(courier.reason()).isEqualTo("UNAVAILABLE_ITEMS");
        assertThat(courier.blockingItems()).containsExactly("Scan Green Board 2 x 3");
        // lorry untouched
        assertThat(optionFor(q, DeliveryMethod.COMPANY_LORRY).available()).isTrue();
    }

    // --- mixed cart: courier-only small board + lorry-only big board (owner 2026-07-17) ---

    /** A small board that ships by courier only: courier on, lorry switched off in every zone. */
    private static CartLine courierOnlySmall(String name, long lorryCents) {
        return new CartLine(name, BoardSizeTier.UNDER_2FT, BigDecimal.ONE,
            lorryCents, lorryCents, lorryCents, null, null, null,
            false, false, false /* lorry OFF all zones */, false, false, true /* courier ON */, false, 1);
    }

    /** A big board that ships by lorry only: courier switched off, lorry on (Colombo/Suburb), outer=WhatsApp. */
    private static CartLine lorryOnlyBig(String name, long lorryCents) {
        return new CartLine(name, BoardSizeTier.BETWEEN_2FT_6FT, new BigDecimal("15"),
            lorryCents, lorryCents, lorryCents, null, null, null,
            true, true, true, true /* outer WhatsApp */, false, false /* courier OFF */, false, 1);
    }

    @Test
    void mixedCartOffersLorryCarryingTheCourierOnlySmallBoard() {
        colomboPostal();
        // 1x1 (courier-only, lorry off) + 6x4 (lorry-only, courier off) ordered together: pre-fix this
        // locked out BOTH rails. Now the lorry-only big board forces the cart onto the lorry, so the small
        // board rides along and the lorry is offered for the whole order.
        DeliveryQuote q = service.options(
            List.of(courierOnlySmall("Scan White Board 1x1", 50000L), lorryOnlyBig("Scan White Board 6 x 4", 100000L)),
            "00100", null, 700000);
        Option lorry = optionFor(q, DeliveryMethod.COMPANY_LORRY);
        assertThat(lorry.available()).isTrue();
        assertThat(lorry.prepaidCents()).isEqualTo(100000);      // Rs 500 + Rs 1,000 = Rs 1,500, capped to Rs 1,000
        // Courier stays hidden — the big board has no courier route.
        Option courier = optionFor(q, DeliveryMethod.COURIER);
        assertThat(courier.available()).isFalse();
        assertThat(courier.reason()).isEqualTo("UNAVAILABLE_ITEMS");
        assertThat(courier.blockingItems()).containsExactly("Scan White Board 6 x 4");
    }

    @Test
    void courierOnlySmallBoardAloneStillHidesLorry() {
        // The ride-along must NOT leak into a pure small-board cart: with no lorry-only item present the
        // small board's lorry-off switch still vetoes the lorry, leaving the courier as the only rail.
        colomboPostal();
        DeliveryQuote q = service.options(
            List.of(courierOnlySmall("Scan White Board 1x1", 50000L)), "00100", null, 700000);
        Option lorry = optionFor(q, DeliveryMethod.COMPANY_LORRY);
        assertThat(lorry.available()).isFalse();
        assertThat(lorry.reason()).isEqualTo("UNAVAILABLE_ITEMS");
        assertThat(lorry.blockingItems()).containsExactly("Scan White Board 1x1");
        assertThat(optionFor(q, DeliveryMethod.COURIER).available()).isTrue();
    }

    @Test
    void mixedCartToOuterAddressRoutesLorryToWhatsapp() {
        // Outer address: the lorry-only big board carries lorry_outer_whatsapp, so the whole outer order is
        // arranged via contact — the mixed-cart ride-along doesn't override the outer WhatsApp rule.
        outerPostal();
        DeliveryQuote q = service.options(
            List.of(courierOnlySmall("Scan White Board 1x1", 50000L), lorryOnlyBig("Scan White Board 6 x 4", 100000L)),
            "20000", null, 700000);
        Option lorry = optionFor(q, DeliveryMethod.COMPANY_LORRY);
        assertThat(lorry.available()).isFalse();
        assertThat(lorry.reason()).isEqualTo("WHATSAPP_OUTER");
    }

    @Test
    void mixedCartWithNonCouriableGlassAlsoForcesLorry() {
        // A non-couriable item (glass, no size tier) is lorry-only too — it likewise lets a courier-preferring
        // small board ride the lorry rather than locking the cart out.
        colomboPostal();
        CartLine glass = new CartLine("Glass Board", null, null, 40000L, 40000L, 40000L,
            null, null, null, true, true, true, false, false, true, false, 1);
        DeliveryQuote q = service.options(
            List.of(courierOnlySmall("Scan White Board 1x1", 50000L), glass), "00100", null, 700000);
        Option lorry = optionFor(q, DeliveryMethod.COMPANY_LORRY);
        assertThat(lorry.available()).isTrue();
        assertThat(lorry.prepaidCents()).isEqualTo(90000);       // Rs 500 + Rs 400, under the Rs 1,000 cap
    }

    // --- large boards blocked TO outer areas only ---

    @Test
    void largeBoardBlockedFromCourierToOuterArea() {
        outerPostal();
        CartLine big = new CartLine("Scan White Board 8 x 4", BoardSizeTier.BETWEEN_2FT_6FT, new BigDecimal("20"),
            200000L, 200000L, 200000L, null, null, null, true, true, true,
            false, true /* courierOuterBlocked */, true, false, 1);
        DeliveryQuote q = service.options(List.of(big), "20000", null, 700000);
        Option courier = optionFor(q, DeliveryMethod.COURIER);
        assertThat(courier.available()).isFalse();
        assertThat(courier.reason()).isEqualTo("OVERSIZE_OUTER");
        assertThat(courier.blockingItems()).containsExactly("Scan White Board 8 x 4");
    }

    @Test
    void largeBoardStillCourierableToCityLimits() {
        colomboPostal();
        CartLine big = new CartLine("Scan White Board 8 x 4", BoardSizeTier.BETWEEN_2FT_6FT, new BigDecimal("20"),
            200000L, 200000L, 200000L, null, null, null, true, true, true,
            false, true /* courierOuterBlocked */, true, false, 1);
        DeliveryQuote q = service.options(List.of(big), "00100", null, 700000);
        Option courier = optionFor(q, DeliveryMethod.COURIER);
        assertThat(courier.available()).isTrue();
        // 20 kg city limits above 2 ft: Rs 380 + 19 × Rs 180 + Rs 750 = Rs 4,550
        assertThat(courier.courierEstimateCents()).isEqualTo(455000);
    }

    @Test
    void nonCouriableItemHidesCourier() {
        outerPostal();
        CartLine glass = new CartLine("Glass Board", null, null, 300000L, 400000L, null,
            null, null, null, true, true, true, false, false, true, false, 1);
        DeliveryQuote q = service.options(List.of(glass), "20000", null, 700000);
        Option courier = optionFor(q, DeliveryMethod.COURIER);
        assertThat(courier.available()).isFalse();
        assertThat(courier.reason()).isEqualTo("MISSING_SIZE_TIER");
    }

    // --- whatsapp-only / outer-contact / admin OFF switch ---

    @Test
    void whatsappOnlyItemHidesAllRails() {
        CartLine glass = new CartLine("Glass Board 8 x 4", null, null, null, null, null,
            null, null, null, true, true, true, false, false, true, true, 1);
        DeliveryQuote q = service.options(List.of(glass), "00100", null, 5000000);
        assertThat(q.whatsappOnly()).isTrue();
        assertThat(q.options()).isEmpty();
    }

    @Test
    void outerContactItemHidesLorryForOuterAddress() {
        outerPostal();
        CartLine keyHolder = new CartLine("Key Holder", null, null, 80000L, 100000L, null,
            null, null, null, true, true, true, true /* outer contact */, false, true, false, 1);
        DeliveryQuote q = service.options(List.of(keyHolder), "20000", null, 700000);
        Option lorry = optionFor(q, DeliveryMethod.COMPANY_LORRY);
        assertThat(lorry.available()).isFalse();
        assertThat(lorry.reason()).isEqualTo("WHATSAPP_OUTER");
    }

    @Test
    void adminDisabledZoneHidesLorryAndListsItems() {
        // owner 2026-07-05: small boards — no lorry to outer; the customer simply uses the courier.
        outerPostal();
        CartLine smallBoard = new CartLine("Scan White Board 1 x 1", BoardSizeTier.UNDER_2FT, BigDecimal.ONE,
            50000L, 50000L, null, null, null, null, true, true, false /* outer OFF */, false, false, true, false, 1);
        DeliveryQuote q = service.options(List.of(smallBoard), "20000", null, 700000);
        Option lorry = optionFor(q, DeliveryMethod.COMPANY_LORRY);
        assertThat(lorry.available()).isFalse();
        assertThat(lorry.reason()).isEqualTo("UNAVAILABLE_ITEMS");
        assertThat(lorry.blockingItems()).containsExactly("Scan White Board 1 x 1");
        // courier untouched
        assertThat(optionFor(q, DeliveryMethod.COURIER).available()).isTrue();
    }

    @Test
    void adminDisabledZoneOnlyAffectsThatZone() {
        colomboPostal();
        CartLine smallBoard = new CartLine("Scan White Board 1 x 1", BoardSizeTier.UNDER_2FT, BigDecimal.ONE,
            50000L, 50000L, null, null, null, null, true, true, false /* outer OFF */, false, false, true, false, 2);
        DeliveryQuote q = service.options(List.of(smallBoard), "00100", null, 700000);
        Option lorry = optionFor(q, DeliveryMethod.COMPANY_LORRY);
        assertThat(lorry.available()).isTrue();                  // Colombo unaffected by the outer switch
        assertThat(lorry.prepaidCents()).isEqualTo(100000);
    }

    // --- lorry pricing + gates ---

    @Test
    void lorrySumsPricedCellTimesQuantity() {
        colomboPostal();
        // 2 × Rs 300 = Rs 600, comfortably under the Rs 1,000 cap, so this isolates the summing logic
        // itself rather than the cap (see the dedicated cap tests below for the capped case).
        DeliveryQuote q = service.options(List.of(board(30000, BoardSizeTier.BETWEEN_2FT_6FT, 2)), "00100", null, 700000);
        Option lorry = optionFor(q, DeliveryMethod.COMPANY_LORRY);
        assertThat(lorry.available()).isTrue();
        assertThat(lorry.prepaidCents()).isEqualTo(60000);
    }

    @Test
    void lorryGatedSmallItemBelowThresholdReportsAddMore() {
        colomboPostal();
        // Colombo gate Rs 3,000, subtotal Rs 1,100 → unavailable, add Rs 1,900 more
        CartLine tableTop = new CartLine("Table Top", BoardSizeTier.UNDER_2FT, BigDecimal.ONE,
            null, null, null, 300000L, 300000L, null, true, true, true, false, false, true, false, 1);
        DeliveryQuote q = service.options(List.of(tableTop), "00100", null, 110000);
        Option lorry = optionFor(q, DeliveryMethod.COMPANY_LORRY);
        assertThat(lorry.available()).isFalse();
        assertThat(lorry.reason()).isEqualTo("MIN_BILL_NOT_MET");
        assertThat(lorry.addMoreCents()).isEqualTo(190000);
    }

    @Test
    void lorryGatedSmallItemOverThresholdChargesFlatOnce() {
        colomboPostal();
        // 4× table tops, subtotal Rs 4,400 > Rs 3,000 gate → flat Rs 1,000 (once, not × qty)
        CartLine tableTop = new CartLine("Table Top", BoardSizeTier.UNDER_2FT, BigDecimal.ONE,
            null, null, null, 300000L, 300000L, null, true, true, true, false, false, true, false, 4);
        DeliveryQuote q = service.options(List.of(tableTop), "00100", null, 440000);
        Option lorry = optionFor(q, DeliveryMethod.COMPANY_LORRY);
        assertThat(lorry.available()).isTrue();
        assertThat(lorry.prepaidCents()).isEqualTo(100000);
    }

    // --- lorry cap (owner 2026-07-07, UNCONDITIONAL): Colombo never exceeds Rs 1,000, Suburb never
    // exceeds Rs 1,500, on ANY order size — not just large orders (a same-day correction after a
    // Rs 1,687 order priced Colombo delivery at Rs 1,600 under an earlier "only above Rs 6,000" cut) ---

    @Test
    void colomboLorryCappedWhenPricedSumExceedsCapOnALargeOrder() {
        colomboPostal();
        // 3× boards at Rs 800 each = Rs 2,400 raw sum → capped at Rs 1,000 regardless of the Rs 7,000 subtotal
        DeliveryQuote q = service.options(List.of(board(80000, BoardSizeTier.BETWEEN_2FT_6FT, 3)), "00100", null, 700000);
        Option lorry = optionFor(q, DeliveryMethod.COMPANY_LORRY);
        assertThat(lorry.available()).isTrue();
        assertThat(lorry.prepaidCents()).isEqualTo(100000);
    }

    @Test
    void colomboLorryCappedOnASmallOrderToo() {
        // Reproduces the exact regression the owner reported: a small order (subtotal well under the
        // old Rs 6,000 trigger) whose per-cell sum still exceeds the cap must ALSO be capped now.
        colomboPostal();
        CartLine boardA = new CartLine("Board A", BoardSizeTier.UNDER_2FT, BigDecimal.ONE,
            80000L, 80000L, 80000L, null, null, null, true, true, true, false, false, true, false, 1);
        CartLine boardB = new CartLine("Board B", BoardSizeTier.UNDER_2FT, BigDecimal.ONE,
            80000L, 80000L, 80000L, null, null, null, true, true, true, false, false, true, false, 1);
        // subtotal ~Rs 1,687 (well below Rs 6,000); raw lorry sum = 2 × Rs 800 = Rs 1,600
        DeliveryQuote q = service.options(List.of(boardA, boardB), "00100", null, 168700);
        Option lorry = optionFor(q, DeliveryMethod.COMPANY_LORRY);
        assertThat(lorry.available()).isTrue();
        assertThat(lorry.prepaidCents()).isEqualTo(100000); // capped, not Rs 1,600
    }

    @Test
    void lorryNotBumpedUpWhenPricedSumIsAlreadyBelowCap() {
        colomboPostal();
        // 1× board at Rs 300 — the cap never bumps a cheaper delivery UP to the ceiling
        CartLine cheapBoard = new CartLine("Small Board", BoardSizeTier.UNDER_2FT, BigDecimal.ONE,
            30000L, 30000L, 30000L, null, null, null, true, true, true, false, false, true, false, 1);
        DeliveryQuote q = service.options(List.of(cheapBoard), "00100", null, 700000);
        Option lorry = optionFor(q, DeliveryMethod.COMPANY_LORRY);
        assertThat(lorry.available()).isTrue();
        assertThat(lorry.prepaidCents()).isEqualTo(30000);
    }

    @Test
    void suburbLorryCappedAtItsHigherAmount() {
        PostalZone zone = new PostalZone("11500", LorryZone.SUBURB, CourierZone.SUBURBS, "Gampaha", "Western Province");
        when(zoneResolver.postalZone("11500")).thenReturn(Optional.of(zone));
        lenient().when(zoneResolver.courierZone(eq("11500"), any())).thenReturn(Optional.of(CourierZone.SUBURBS));
        // 3× boards at Rs 800 each = Rs 2,400 raw sum → capped at Rs 1,500 (Suburb)
        DeliveryQuote q = service.options(List.of(board(80000, BoardSizeTier.BETWEEN_2FT_6FT, 3)), "11500", null, 700000);
        Option lorry = optionFor(q, DeliveryMethod.COMPANY_LORRY);
        assertThat(lorry.available()).isTrue();
        assertThat(lorry.prepaidCents()).isEqualTo(150000);
    }

    @Test
    void outerZoneIsNeverCapped() {
        outerPostal();
        // 3× boards at Rs 800 each = Rs 2,400 — Outer has no cap, ever
        DeliveryQuote q = service.options(List.of(board(80000, BoardSizeTier.BETWEEN_2FT_6FT, 3)), "20000", null, 700000);
        Option lorry = optionFor(q, DeliveryMethod.COMPANY_LORRY);
        assertThat(lorry.available()).isTrue();
        assertThat(lorry.prepaidCents()).isEqualTo(240000); // uncapped
    }

    @Test
    void gatedCellWithOwnPriceChargesPerUnitWhenMet() {
        // owner 2026-07-05: a gated cell may carry its own price — used instead of the flat charge.
        colomboPostal();
        CartLine item = new CartLine("Acrylic Board", BoardSizeTier.UNDER_2FT, BigDecimal.ONE,
            30000L /* Rs 300 when gate met */, null, null, 300000L, null, null,
            true, true, true, false, false, true, false, 3);
        DeliveryQuote q = service.options(List.of(item), "00100", null, 400000);
        Option lorry = optionFor(q, DeliveryMethod.COMPANY_LORRY);
        assertThat(lorry.available()).isTrue();
        assertThat(lorry.prepaidCents()).isEqualTo(90000);       // 3 × Rs 300, no flat charge
    }

    @Test
    void outerGateUsesOuterFlatChargeWhenMet() {
        // Gates now work on the outer zone too (owner 2026-07-05).
        outerPostal();
        CartLine item = new CartLine("Small Item", BoardSizeTier.UNDER_2FT, BigDecimal.ONE,
            null, null, null, null, null, 600000L /* outer gate Rs 6,000 */,
            true, true, true, false, false, true, false, 1);
        DeliveryQuote q = service.options(List.of(item), "20000", null, 700000);
        Option lorry = optionFor(q, DeliveryMethod.COMPANY_LORRY);
        assertThat(lorry.available()).isTrue();
        assertThat(lorry.prepaidCents()).isEqualTo(100000);      // outer flat gate-met charge (Rs 1,000)
    }

    // --- serviceability ---

    @Test
    void unknownPostalCodeIsNonServiceableForLorryAndCourier() {
        when(zoneResolver.postalZone("99999")).thenReturn(Optional.empty());
        when(zoneResolver.courierZone(eq("99999"), any())).thenReturn(Optional.empty());
        DeliveryQuote q = service.options(List.of(board(80000, BoardSizeTier.BETWEEN_2FT_6FT, 1)), "99999", null, 700000);
        assertThat(q.postalServiceable()).isFalse();
        assertThat(optionFor(q, DeliveryMethod.COMPANY_LORRY).reason()).isEqualTo("NOT_SERVICEABLE_POSTAL");
        assertThat(optionFor(q, DeliveryMethod.COURIER).reason()).isEqualTo("NOT_SERVICEABLE_POSTAL");
    }

    @Test
    void unknownPostalButCourierCityStillOffersCourier() {
        when(zoneResolver.postalZone("99999")).thenReturn(Optional.empty());
        when(zoneResolver.courierZone(eq("99999"), any())).thenReturn(Optional.of(CourierZone.CITY_LIMITS));
        DeliveryQuote q = service.options(List.of(board(80000, BoardSizeTier.BETWEEN_2FT_6FT, 1)), "99999", "Colombo", 700000);
        Option courier = optionFor(q, DeliveryMethod.COURIER);
        assertThat(courier.available()).isTrue();
        assertThat(courier.courierEstimateCents()).isEqualTo(113000);
    }
}
