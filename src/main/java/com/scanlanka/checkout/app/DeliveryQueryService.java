package com.scanlanka.checkout.app;

import com.scanlanka.checkout.domain.LorryZone;
import com.scanlanka.checkout.domain.PostalZone;
import com.scanlanka.checkout.infra.PostalZoneRepository;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Public delivery serviceability lookups (05 FR-CHECKOUT-3/3c), now over the two-rail `postal_zone`
 * mapping (17): a postal code is serviceable if it's mapped. The picker lists mapped codes; the
 * Delivery Locations page groups them by in-house lorry zone (Colombo / suburb / outer).
 */
@Service
@Transactional(readOnly = true)
public class DeliveryQueryService {

    private final PostalZoneRepository postalZones;

    public DeliveryQueryService(PostalZoneRepository postalZones) {
        this.postalZones = postalZones;
    }

    public record PostalCodeView(String postalCode, String zoneName) {}

    public record ZoneLocationView(String zone, List<String> postalCodes) {}

    @Cacheable(value = "delivery-postal", key = "#q == null ? '' : #q")
    public List<PostalCodeView> postalCodes(String q) {
        String needle = q == null ? "" : q.trim();
        return postalZones.findAll().stream()
            .filter(z -> needle.isEmpty() || z.getPostalCode().startsWith(needle))
            .map(z -> new PostalCodeView(z.getPostalCode(), label(z)))
            .sorted((a, b) -> a.postalCode().compareTo(b.postalCode()))
            .toList();
    }

    @Cacheable("delivery-locations")
    public List<ZoneLocationView> locations() {
        Map<LorryZone, List<String>> byZone = new EnumMap<>(LorryZone.class);
        for (PostalZone z : postalZones.findAll()) {
            byZone.computeIfAbsent(z.getLorryZone(), k -> new ArrayList<>()).add(z.getPostalCode());
        }
        List<ZoneLocationView> out = new ArrayList<>();
        for (var e : byZone.entrySet()) {
            List<String> codes = e.getValue();
            codes.sort(String::compareTo);
            out.add(new ZoneLocationView(e.getKey().name(), List.copyOf(codes)));
        }
        return out;
    }

    /** A human label for the picker: the district if known, else the lorry zone. */
    private static String label(PostalZone z) {
        return z.getDistrict() != null && !z.getDistrict().isBlank()
            ? z.getDistrict() : z.getLorryZone().name();
    }
}
