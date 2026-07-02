package com.scanlanka.checkout.app;

import com.scanlanka.checkout.domain.CourierArea;
import com.scanlanka.checkout.domain.CourierZone;
import com.scanlanka.checkout.domain.PostalZone;
import com.scanlanka.checkout.infra.CourierAreaRepository;
import com.scanlanka.checkout.infra.PostalZoneRepository;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Resolves the Citrek courier band from checkout city (client area list) with postal-code fallback.
 * Lorry zones always come from {@link PostalZone}; only courier pricing band is city-overridable.
 */
@Component
public class CourierZoneResolver {

    private static final Pattern NON_ALNUM = Pattern.compile("[^a-z0-9]+");
    private static final Pattern SPLIT = Pattern.compile("[,;/|\\\\-]+|\\s+");

    private final CourierAreaRepository areas;
    private final PostalZoneRepository postalZones;

    public CourierZoneResolver(CourierAreaRepository areas, PostalZoneRepository postalZones) {
        this.areas = areas;
        this.postalZones = postalZones;
    }

    public Optional<PostalZone> postalZone(String postalCode) {
        if (postalCode == null || postalCode.isBlank()) return Optional.empty();
        return postalZones.findById(postalCode.trim());
    }

    /** Courier band for a shipment. City match wins over the postal row's default courier zone. */
    public Optional<CourierZone> courierZone(String postalCode, String city) {
        Optional<CourierZone> fromCity = fromCity(city);
        if (fromCity.isPresent()) return fromCity;
        return postalZone(postalCode).map(PostalZone::getCourierZone);
    }

    public Optional<CourierZone> fromCity(String city) {
        if (city == null || city.isBlank()) return Optional.empty();
        CourierZone best = null;
        int bestRank = -1;
        for (String part : SPLIT.split(city)) {
            String token = part.trim();
            if (token.isEmpty()) continue;
            Optional<CourierArea> hit = areas.findById(normalize(token));
            if (hit.isPresent()) {
                int rank = rank(hit.get().getCourierZone());
                if (rank > bestRank) {
                    bestRank = rank;
                    best = hit.get().getCourierZone();
                }
            }
        }
        Optional<CourierArea> whole = areas.findById(normalize(city));
        if (whole.isPresent()) {
            int rank = rank(whole.get().getCourierZone());
            if (rank > bestRank) return Optional.of(whole.get().getCourierZone());
        }
        return best != null ? Optional.of(best) : Optional.empty();
    }

    static String normalize(String name) {
        if (name == null) return "";
        String s = Normalizer.normalize(name, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        s = NON_ALNUM.matcher(s.toLowerCase(Locale.ROOT)).replaceAll("");
        return s;
    }

    private static int rank(CourierZone zone) {
        return switch (zone) {
            case FARAWAY -> 3;
            case OUTSTATION -> 2;
            case SUBURBS -> 1;
            case CITY_LIMITS -> 0;
        };
    }
}
