package com.scanlanka.admin.app;

import com.scanlanka.checkout.domain.DeliveryZone;
import com.scanlanka.checkout.domain.DeliveryZonePostalCode;
import com.scanlanka.checkout.infra.DeliveryZonePostalCodeRepository;
import com.scanlanka.checkout.infra.DeliveryZoneRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;

/** Admin CRUD for delivery zones + postal codes (08 FR-ADMIN-1/2). */
@Service
public class DeliveryZoneAdminService {

    private final DeliveryZoneRepository zones;
    private final DeliveryZonePostalCodeRepository postalCodes;
    private final AuditService audit;
    private final DeliveryCacheEvictor cache;

    public DeliveryZoneAdminService(DeliveryZoneRepository zones, DeliveryZonePostalCodeRepository postalCodes,
                                    AuditService audit, DeliveryCacheEvictor cache) {
        this.zones = zones;
        this.postalCodes = postalCodes;
        this.audit = audit;
        this.cache = cache;
    }

    public record ZoneView(long id, String name, long baseChargeCents, long perKgChargeCents,
                           BigDecimal fuelPct, boolean active, List<String> postalCodes) {}
    public record ZoneRequest(String name, long baseChargeCents, long perKgChargeCents,
                              BigDecimal fuelPct, boolean active, List<String> postalCodes) {}

    @Transactional(readOnly = true)
    public List<ZoneView> list() {
        return zones.findAll().stream().map(this::toView).toList();
    }

    @Transactional(readOnly = true)
    public ZoneView get(long id) {
        return toView(load(id));
    }

    @Transactional
    public ZoneView create(ZoneRequest req, Long adminId) {
        DeliveryZone zone = zones.save(new DeliveryZone(
            req.name(), req.baseChargeCents(), req.perKgChargeCents(), req.fuelPct(), req.active()));
        replacePostalCodes(zone.getId(), req.postalCodes());
        audit.log(adminId, "ZONE_CREATE", "delivery_zone", String.valueOf(zone.getId()), null, req.name());
        cache.evictAll();
        return toView(zone);
    }

    @Transactional
    public ZoneView update(long id, ZoneRequest req, Long adminId) {
        DeliveryZone zone = load(id);
        String before = zone.getName();
        zone.update(req.name(), req.baseChargeCents(), req.perKgChargeCents(), req.fuelPct(), req.active());
        zones.save(zone);
        replacePostalCodes(id, req.postalCodes());
        audit.log(adminId, "ZONE_UPDATE", "delivery_zone", String.valueOf(id), before, req.name());
        cache.evictAll();
        return toView(zone);
    }

    @Transactional
    public void delete(long id, Long adminId) {
        DeliveryZone zone = load(id);
        postalCodes.deleteByZoneId(id);
        zones.delete(zone);
        audit.log(adminId, "ZONE_DELETE", "delivery_zone", String.valueOf(id), zone.getName(), null);
        cache.evictAll();
    }

    private void replacePostalCodes(long zoneId, List<String> codes) {
        postalCodes.deleteByZoneId(zoneId);
        if (codes == null) return;
        for (String raw : codes) {
            String pc = normalizePostal(raw);
            if (pc.isEmpty()) continue;
            if (postalCodes.existsByPostalCodeAndZoneIdNot(pc, zoneId)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "POSTAL_CODE_IN_ANOTHER_ZONE:" + pc);
            }
            postalCodes.save(new DeliveryZonePostalCode(pc, zoneId));
        }
    }

    private ZoneView toView(DeliveryZone zone) {
        List<String> pcs = postalCodes.findByZoneId(zone.getId()).stream()
            .map(DeliveryZonePostalCode::getPostalCode).sorted().toList();
        return new ZoneView(zone.getId(), zone.getName(), zone.getBaseChargeCents(), zone.getPerKgChargeCents(),
            zone.getFuelPct(), zone.isActive(), pcs);
    }

    private DeliveryZone load(long id) {
        return zones.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Zone not found"));
    }

    private static String normalizePostal(String raw) {
        return raw == null ? "" : raw.trim().toUpperCase();
    }
}
