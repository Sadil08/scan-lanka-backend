package com.scanlanka.admin.app;

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
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * Admin config for the two-rail delivery model (08 FR-ADMIN-1/2, 17): the Citrek courier rate card,
 * the global lorry minimum bill, per-rail enable toggles, and the postal-code → zone mapping. Changes
 * take effect for NEW quotes immediately (the options service reads these live); placed orders keep
 * their snapshot (FR-ADMIN-8). Every change is audited.
 */
@Service
public class AdminDeliveryConfigService {

    private final CourierRateCardRepository courierRates;
    private final DeliverySettingsRepository settings;
    private final DeliveryMethodConfigRepository methods;
    private final PostalZoneRepository postalZones;
    private final AuditService audit;
    private final DeliveryCacheEvictor cache;

    public AdminDeliveryConfigService(CourierRateCardRepository courierRates, DeliverySettingsRepository settings,
                                      DeliveryMethodConfigRepository methods, PostalZoneRepository postalZones,
                                      AuditService audit, DeliveryCacheEvictor cache) {
        this.courierRates = courierRates;
        this.settings = settings;
        this.methods = methods;
        this.postalZones = postalZones;
        this.audit = audit;
        this.cache = cache;
    }

    public record CourierRateView(CourierZone zone, long baseCents, long perKgCents) {}
    public record CourierRateRequest(long baseCents, long perKgCents) {}
    public record SettingsView(long lorryMinBillCents) {}
    public record SettingsRequest(long lorryMinBillCents) {}
    public record MethodView(DeliveryMethod method, boolean enabled) {}
    public record MethodRequest(boolean enabled) {}
    public record PostalZoneRequest(LorryZone lorryZone, CourierZone courierZone, String district, String province) {}
    public record PostalZoneView(String postalCode, LorryZone lorryZone, CourierZone courierZone,
                                 String district, String province) {}

    // --- Courier rate card ---

    @Transactional(readOnly = true)
    public List<CourierRateView> listCourierRates() {
        return courierRates.findAll().stream()
            .map(r -> new CourierRateView(r.getZone(), r.getBaseCents(), r.getPerKgCents())).toList();
    }

    @Transactional
    public CourierRateView upsertCourierRate(CourierZone zone, CourierRateRequest req, Long adminId) {
        if (req.baseCents() < 0 || req.perKgCents() < 0) throw badRequest("NEGATIVE_RATE");
        CourierRateCard card = courierRates.findById(zone)
            .map(c -> { c.update(req.baseCents(), req.perKgCents()); return c; })
            .orElseGet(() -> new CourierRateCard(zone, req.baseCents(), req.perKgCents()));
        courierRates.save(card);
        audit.log(adminId, "COURIER_RATE_UPDATE", "courier_rate_card", zone.name(), null,
            req.baseCents() + "/" + req.perKgCents());
        cache.evictAll();
        return new CourierRateView(zone, req.baseCents(), req.perKgCents());
    }

    // --- Global lorry minimum bill ---

    @Transactional(readOnly = true)
    public SettingsView getSettings() {
        return new SettingsView(loadSettings().getLorryMinBillCents());
    }

    @Transactional
    public SettingsView updateSettings(SettingsRequest req, Long adminId) {
        if (req.lorryMinBillCents() < 0) throw badRequest("NEGATIVE_MIN_BILL");
        DeliverySettings s = loadSettings();
        long before = s.getLorryMinBillCents();
        s.setLorryMinBillCents(req.lorryMinBillCents());
        settings.save(s);
        audit.log(adminId, "DELIVERY_SETTINGS_UPDATE", "delivery_settings", "1",
            String.valueOf(before), String.valueOf(req.lorryMinBillCents()));
        cache.evictAll();
        return new SettingsView(req.lorryMinBillCents());
    }

    // --- Per-rail enable toggle ---

    @Transactional(readOnly = true)
    public List<MethodView> listMethods() {
        return methods.findAll().stream()
            .map(m -> new MethodView(m.getMethod(), m.isEnabled())).toList();
    }

    @Transactional
    public MethodView setMethodEnabled(DeliveryMethod method, MethodRequest req, Long adminId) {
        DeliveryMethodConfig cfg = methods.findById(method)
            .map(m -> { m.setEnabled(req.enabled()); return m; })
            .orElseGet(() -> new DeliveryMethodConfig(method, req.enabled()));
        methods.save(cfg);
        audit.log(adminId, "DELIVERY_METHOD_TOGGLE", "delivery_method_config", method.name(), null,
            String.valueOf(req.enabled()));
        cache.evictAll();
        return new MethodView(method, req.enabled());
    }

    // --- Postal-code → zone mapping ---

    @Transactional(readOnly = true)
    public PostalZoneView getPostalZone(String postalCode) {
        PostalZone z = postalZones.findById(normalize(postalCode))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Postal code not mapped"));
        return toView(z);
    }

    @Transactional
    public PostalZoneView upsertPostalZone(String postalCode, PostalZoneRequest req, Long adminId) {
        String pc = normalize(postalCode);
        if (req.lorryZone() == null || req.courierZone() == null) throw badRequest("ZONE_REQUIRED");
        PostalZone z = postalZones.save(
            new PostalZone(pc, req.lorryZone(), req.courierZone(), req.district(), req.province()));
        audit.log(adminId, "POSTAL_ZONE_UPSERT", "postal_zone", pc, null,
            req.lorryZone() + "/" + req.courierZone());
        cache.evictAll();
        return toView(z);
    }

    @Transactional
    public void deletePostalZone(String postalCode, Long adminId) {
        String pc = normalize(postalCode);
        postalZones.deleteById(pc);
        audit.log(adminId, "POSTAL_ZONE_DELETE", "postal_zone", pc, null, null);
        cache.evictAll();
    }

    // --- helpers ---

    private DeliverySettings loadSettings() {
        return settings.findFirstByOrderByIdAsc()
            .orElseThrow(() -> new IllegalStateException("delivery_settings missing"));
    }

    private static PostalZoneView toView(PostalZone z) {
        return new PostalZoneView(z.getPostalCode(), z.getLorryZone(), z.getCourierZone(),
            z.getDistrict(), z.getProvince());
    }

    private static String normalize(String postalCode) {
        return postalCode == null ? "" : postalCode.trim();
    }

    private static ResponseStatusException badRequest(String code) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, code);
    }
}
