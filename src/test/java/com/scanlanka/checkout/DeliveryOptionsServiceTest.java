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
import com.scanlanka.checkout.domain.CourierRateCardId;
import com.scanlanka.checkout.domain.CourierZone;
import com.scanlanka.checkout.domain.DeliveryMethod;
import com.scanlanka.checkout.domain.DeliveryMethodConfig;
import com.scanlanka.checkout.domain.DeliverySettings;
import com.scanlanka.checkout.domain.LorryZone;
import com.scanlanka.checkout.domain.PostalZone;
import com.scanlanka.checkout.infra.CourierRateCardRepository;
import com.scanlanka.checkout.infra.DeliveryMethodConfigRepository;
import com.scanlanka.checkout.infra.DeliverySettingsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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
        lenient().when(s.getLorryMinBillCents()).thenReturn(600000L);
        lenient().when(settings.findFirstByOrderByIdAsc()).thenReturn(Optional.of(s));
        lenient().when(courierRates.findById(new CourierRateCardId(CourierZone.CITY_LIMITS, BoardSizeTier.BETWEEN_2FT_6FT)))
            .thenReturn(Optional.of(new CourierRateCard(CourierZone.CITY_LIMITS, BoardSizeTier.BETWEEN_2FT_6FT, 100000)));
        lenient().when(courierRates.findById(new CourierRateCardId(CourierZone.SUBURBS, BoardSizeTier.BETWEEN_2FT_6FT)))
            .thenReturn(Optional.of(new CourierRateCard(CourierZone.SUBURBS, BoardSizeTier.BETWEEN_2FT_6FT, 125000)));
    }

    private void colomboPostal() {
        PostalZone zone = new PostalZone("00100", LorryZone.COLOMBO, CourierZone.CITY_LIMITS, "Colombo", "Western Province");
        when(zoneResolver.postalZone("00100")).thenReturn(Optional.of(zone));
        when(zoneResolver.courierZone(eq("00100"), any())).thenReturn(Optional.of(CourierZone.CITY_LIMITS));
    }

    private CartLine board(long colomboCents, BoardSizeTier tier, int qty) {
        return new CartLine(tier, colomboCents, null, null, false, qty);
    }

    @Test
    void courierUsesCityZoneOverride() {
        colomboPostal();
        when(zoneResolver.courierZone("00100", "Dehiwala")).thenReturn(Optional.of(CourierZone.SUBURBS));
        DeliveryQuote q = service.options(List.of(board(80000, BoardSizeTier.BETWEEN_2FT_6FT, 1)), "00100", "Dehiwala", 700000);
        Option courier = optionFor(q, DeliveryMethod.COURIER);
        assertThat(courier.courierEstimateCents()).isEqualTo(125000);
    }

    @Test
    void whatsappOnlyItemHidesAllRails() {
        CartLine glass = new CartLine(null, null, null, null, true, 1);
        DeliveryQuote q = service.options(List.of(glass), "00100", null, 5000000);
        assertThat(q.whatsappOnly()).isTrue();
        assertThat(q.options()).isEmpty();
    }

    @Test
    void lorryAvailableOverMinBillSumsCharge() {
        colomboPostal();
        DeliveryQuote q = service.options(List.of(board(80000, BoardSizeTier.BETWEEN_2FT_6FT, 2)), "00100", null, 700000);
        Option lorry = optionFor(q, DeliveryMethod.COMPANY_LORRY);
        assertThat(lorry.available()).isTrue();
        assertThat(lorry.prepaidCents()).isEqualTo(160000);
    }

    @Test
    void courierHiddenWhenAnyItemLacksSizeTier() {
        colomboPostal();
        CartLine noTier = new CartLine(null, 80000L, null, null, false, 1);
        DeliveryQuote q = service.options(List.of(noTier), "00100", null, 700000);
        Option courier = optionFor(q, DeliveryMethod.COURIER);
        assertThat(courier.available()).isFalse();
        assertThat(courier.reason()).isEqualTo("MISSING_SIZE_TIER");
    }

    @Test
    void unknownPostalCodeIsNonServiceableForBothRails() {
        when(zoneResolver.postalZone("99999")).thenReturn(Optional.empty());
        when(zoneResolver.courierZone(eq("99999"), any())).thenReturn(Optional.empty());
        DeliveryQuote q = service.options(List.of(board(80000, BoardSizeTier.BETWEEN_2FT_6FT, 1)), "99999", null, 700000);
        assertThat(q.postalServiceable()).isFalse();
        assertThat(optionFor(q, DeliveryMethod.COMPANY_LORRY).reason()).isEqualTo("NOT_SERVICEABLE_POSTAL");
    }

    private static Option optionFor(DeliveryQuote q, DeliveryMethod method) {
        return q.options().stream().filter(o -> o.method() == method).findFirst().orElseThrow();
    }
}
