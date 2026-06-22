package com.scanlanka.checkout.app;

import com.scanlanka.checkout.domain.DeliveryZone;
import com.scanlanka.checkout.domain.DeliveryZonePostalCode;
import com.scanlanka.checkout.infra.DeliveryZonePostalCodeRepository;
import com.scanlanka.checkout.infra.DeliveryZoneRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Public delivery zone/postal lookups (05 FR-CHECKOUT-3/3c). */
@Service
@Transactional(readOnly = true)
public class DeliveryQueryService {

    private final DeliveryZonePostalCodeRepository postalCodes;
    private final DeliveryZoneRepository zones;

    public DeliveryQueryService(DeliveryZonePostalCodeRepository postalCodes,
                                DeliveryZoneRepository zones) {
        this.postalCodes = postalCodes;
        this.zones = zones;
    }

    public record PostalCodeView(String postalCode, String zoneName) {}

    public record ZoneLocationView(String zone, List<String> postalCodes) {}

    public List<PostalCodeView> postalCodes(String q) {
        String needle = q == null ? "" : q.trim();
        return postalCodes.findAll().stream()
            .filter(pc -> activeZone(pc.getZoneId()))
            .filter(pc -> needle.isEmpty() || pc.getPostalCode().startsWith(needle))
            .map(pc -> new PostalCodeView(pc.getPostalCode(), zoneName(pc.getZoneId())))
            .sorted((a, b) -> a.postalCode().compareTo(b.postalCode()))
            .toList();
    }

    public List<ZoneLocationView> locations() {
        Map<Long, List<String>> byZone = new LinkedHashMap<>();
        for (DeliveryZonePostalCode pc : postalCodes.findAll()) {
            if (!activeZone(pc.getZoneId())) continue;
            byZone.computeIfAbsent(pc.getZoneId(), k -> new ArrayList<>()).add(pc.getPostalCode());
        }
        List<ZoneLocationView> out = new ArrayList<>();
        for (var e : byZone.entrySet()) {
            zones.findById(e.getKey()).ifPresent(z ->
                out.add(new ZoneLocationView(z.getName(), List.copyOf(e.getValue()))));
        }
        return out;
    }

    private boolean activeZone(Long zoneId) {
        return zones.findById(zoneId).map(DeliveryZone::isActive).orElse(false);
    }

    private String zoneName(Long zoneId) {
        return zones.findById(zoneId).map(DeliveryZone::getName).orElse("");
    }
}
