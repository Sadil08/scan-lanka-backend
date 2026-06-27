package com.scanlanka.checkout;

import com.scanlanka.checkout.app.CourierEstimateEngine;
import com.scanlanka.checkout.app.DeliveryOptionsService;
import com.scanlanka.checkout.app.DeliveryOptionsService.CartLine;
import com.scanlanka.checkout.app.DeliveryOptionsService.DeliveryQuote;
import com.scanlanka.checkout.app.DeliveryOptionsService.Option;
import com.scanlanka.checkout.app.LorryCostEngine;
import com.scanlanka.checkout.domain.CourierRateCard;
import com.scanlanka.checkout.domain.CourierZone;
import com.scanlanka.checkout.domain.DeliveryMethod;
import com.scanlanka.checkout.domain.DeliveryMethodConfig;
import com.scanlanka.checkout.domain.DeliverySettings;
import com.scanlanka.checkout.domain.LorryZone;
import com.scanlanka.checkout.domain.PostalZone;
import com.scanlanka.checkout.infra.CourierRateCardRepository;
import com.scanlanka.checkout.infra.DeliveryMethodConfigRepository;
import com.scanlanka.checkout.infra.DeliverySettingsRepository;
import com.scanlanka.checkout.infra.PostalZoneRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DeliveryOptionsServiceTest {

    private final PostalZoneRepository postalZones = mock(PostalZoneRepository.class);
    private final CourierRateCardRepository courierRates = mock(CourierRateCardRepository.class);
    private final DeliveryMethodConfigRepository methodConfig = mock(DeliveryMethodConfigRepository.class);
    private final DeliverySettingsRepository settings = mock(DeliverySettingsRepository.class);

    private final DeliveryOptionsService service = new DeliveryOptionsService(
        postalZones, courierRates, methodConfig, settings, new LorryCostEngine(), new CourierEstimateEngine());

    @BeforeEach
    void defaults() {
        // both rails enabled by default (no config row → enabled), min bill Rs 6,000
        lenient().when(methodConfig.findById(any())).thenReturn(Optional.empty());
        DeliverySettings s = mock(DeliverySettings.class);
        lenient().when(s.getLorryMinBillCents()).thenReturn(600000L);
        lenient().when(settings.findFirstByOrderByIdAsc()).thenReturn(Optional.of(s));
        lenient().when(courierRates.findById(CourierZone.COLOMBO_1_15))
            .thenReturn(Optional.of(new CourierRateCard(CourierZone.COLOMBO_1_15, 47000, 18500)));
    }

    private void colomboPostal() {
        when(postalZones.findById("00100")).thenReturn(Optional.of(
            new PostalZone("00100", LorryZone.COLOMBO, CourierZone.COLOMBO_1_15, "Colombo", "Western Province")));
    }

    private CartLine board(long colomboCents, double weightKg, int qty) {
        return new CartLine(BigDecimal.valueOf(weightKg), colomboCents, null, null, false, qty);
    }

    @Test
    void whatsappOnlyItemHidesAllRails() {
        CartLine glass = new CartLine(null, null, null, null, true, 1);
        DeliveryQuote q = service.options(List.of(glass), "00100", 5000000);
        assertThat(q.whatsappOnly()).isTrue();
        assertThat(q.options()).isEmpty();
    }

    @Test
    void lorryAvailableOverMinBillSumsCharge() {
        colomboPostal();
        DeliveryQuote q = service.options(List.of(board(80000, 5, 2)), "00100", 700000);
        Option lorry = optionFor(q, DeliveryMethod.COMPANY_LORRY);
        assertThat(lorry.available()).isTrue();
        assertThat(lorry.prepaidCents()).isEqualTo(160000); // 2 × Rs 800
        assertThat(lorry.someArranged()).isFalse();
    }

    @Test
    void lorryUnavailableAtOrBelowMinBill() {
        colomboPostal();
        DeliveryQuote q = service.options(List.of(board(80000, 5, 1)), "00100", 600000); // exactly 6,000
        Option lorry = optionFor(q, DeliveryMethod.COMPANY_LORRY);
        assertThat(lorry.available()).isFalse();
        assertThat(lorry.reason()).isEqualTo("MIN_BILL_NOT_MET");
    }

    @Test
    void unpricedLorryCellStillOffersLorryAndFlagsArranged() {
        colomboPostal();
        CartLine noColomboPrice = new CartLine(BigDecimal.ONE, null, null, null, false, 1);
        DeliveryQuote q = service.options(List.of(noColomboPrice), "00100", 700000);
        Option lorry = optionFor(q, DeliveryMethod.COMPANY_LORRY);
        assertThat(lorry.available()).isTrue();
        assertThat(lorry.prepaidCents()).isZero();
        assertThat(lorry.someArranged()).isTrue();
    }

    @Test
    void courierEstimateFromWeight() {
        colomboPostal();
        DeliveryQuote q = service.options(List.of(board(80000, 5, 1)), "00100", 700000);
        Option courier = optionFor(q, DeliveryMethod.COURIER);
        assertThat(courier.available()).isTrue();
        assertThat(courier.courierEstimateCents()).isEqualTo(139500); // 5×185 + 470
        assertThat(courier.prepaidCents()).isZero();                  // courier is full COD
    }

    @Test
    void courierHiddenWhenAnyItemLacksWeight() {
        colomboPostal();
        CartLine noWeight = new CartLine(null, 80000L, null, null, false, 1);
        DeliveryQuote q = service.options(List.of(noWeight), "00100", 700000);
        Option courier = optionFor(q, DeliveryMethod.COURIER);
        assertThat(courier.available()).isFalse();
        assertThat(courier.reason()).isEqualTo("MISSING_WEIGHT");
    }

    @Test
    void unknownPostalCodeIsNonServiceableForBothRails() {
        when(postalZones.findById("99999")).thenReturn(Optional.empty());
        DeliveryQuote q = service.options(List.of(board(80000, 5, 1)), "99999", 700000);
        assertThat(q.postalServiceable()).isFalse();
        assertThat(optionFor(q, DeliveryMethod.COMPANY_LORRY).reason()).isEqualTo("NOT_SERVICEABLE_POSTAL");
        assertThat(optionFor(q, DeliveryMethod.COURIER).reason()).isEqualTo("NOT_SERVICEABLE_POSTAL");
    }

    @Test
    void disabledCourierRailIsOmitted() {
        colomboPostal();
        when(methodConfig.findById(DeliveryMethod.COURIER))
            .thenReturn(Optional.of(new DeliveryMethodConfig(DeliveryMethod.COURIER, false)));
        DeliveryQuote q = service.options(List.of(board(80000, 5, 1)), "00100", 700000);
        assertThat(q.options()).extracting(Option::method).containsExactly(DeliveryMethod.COMPANY_LORRY);
    }

    private static Option optionFor(DeliveryQuote q, DeliveryMethod method) {
        return q.options().stream().filter(o -> o.method() == method).findFirst().orElseThrow();
    }
}
