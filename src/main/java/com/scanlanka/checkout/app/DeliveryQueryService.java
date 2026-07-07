package com.scanlanka.checkout.app;

import com.scanlanka.checkout.domain.LorryZone;
import com.scanlanka.checkout.domain.PostalZone;
import com.scanlanka.checkout.infra.PostalZoneRepository;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Public delivery serviceability lookups (05 FR-CHECKOUT-3/3c), now over the two-rail `postal_zone`
 * mapping (17): a postal code is serviceable if it's mapped. The picker lists mapped codes; the
 * Delivery Locations page groups them by in-house lorry zone (Colombo / suburb / outer), summarized
 * as district + count rather than a flat postal-code dump — the seeded set (LK.txt, V28) plus admin
 * additions runs to ~1,800 codes, unusable as one comma-separated list (17, owner 2026-07-06).
 */
@Service
@Transactional(readOnly = true)
public class DeliveryQueryService {

    private final PostalZoneRepository postalZones;

    public DeliveryQueryService(PostalZoneRepository postalZones) {
        this.postalZones = postalZones;
    }

    public record PostalCodeView(String postalCode, String zoneName) {}

    public record DistrictSummary(String district, int count) {}

    public record ZoneLocationView(String zone, int totalCodes, List<DistrictSummary> districts) {}

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
        Map<LorryZone, Map<String, Integer>> byZone = new EnumMap<>(LorryZone.class);
        for (PostalZone z : postalZones.findAll()) {
            String district = z.getDistrict() != null && !z.getDistrict().isBlank() ? z.getDistrict() : "Other";
            byZone.computeIfAbsent(z.getLorryZone(), k -> new LinkedHashMap<>())
                .merge(district, 1, Integer::sum);
        }
        List<ZoneLocationView> out = new ArrayList<>();
        for (var e : byZone.entrySet()) {
            List<DistrictSummary> districts = e.getValue().entrySet().stream()
                .map(d -> new DistrictSummary(d.getKey(), d.getValue()))
                .sorted(Comparator.comparing(DistrictSummary::district))
                .toList();
            int total = districts.stream().mapToInt(DistrictSummary::count).sum();
            out.add(new ZoneLocationView(e.getKey().name(), total, districts));
        }
        return out;
    }

    /** A human label for the picker: the district if known, else the lorry zone. */
    private static String label(PostalZone z) {
        return z.getDistrict() != null && !z.getDistrict().isBlank()
            ? z.getDistrict() : z.getLorryZone().name();
    }
}
