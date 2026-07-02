package com.scanlanka.checkout;

import com.scanlanka.checkout.app.CourierZoneResolver;
import com.scanlanka.checkout.domain.CourierArea;
import com.scanlanka.checkout.domain.CourierZone;
import com.scanlanka.checkout.infra.CourierAreaRepository;
import com.scanlanka.checkout.infra.PostalZoneRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CourierZoneResolverTest {

    private final CourierAreaRepository areas = mock(CourierAreaRepository.class);
    private final PostalZoneRepository postalZones = mock(PostalZoneRepository.class);
    private final CourierZoneResolver resolver = new CourierZoneResolver(areas, postalZones);

    @Test
    void normalizesAndMatchesSuburb() {
        CourierArea dehiwala = zone(CourierZone.SUBURBS);
        when(areas.findById("dehiwala")).thenReturn(Optional.of(dehiwala));
        assertThat(resolver.fromCity("Dehiwala")).contains(CourierZone.SUBURBS);
        assertThat(resolver.fromCity("dehiwala-mount lavinia")).contains(CourierZone.SUBURBS);
    }

    @Test
    void farAwayWinsOverOutstationOnMultiPartCity() {
        CourierArea galle = zone(CourierZone.OUTSTATION);
        CourierArea yala = zone(CourierZone.FARAWAY);
        when(areas.findById("galle")).thenReturn(Optional.of(galle));
        when(areas.findById("yala")).thenReturn(Optional.of(yala));
        assertThat(resolver.fromCity("Galle, Yala")).contains(CourierZone.FARAWAY);
    }

    private static CourierArea zone(CourierZone courierZone) {
        CourierArea a = mock(CourierArea.class);
        when(a.getCourierZone()).thenReturn(courierZone);
        return a;
    }
}
