package com.scanlanka.admin.web;

import com.scanlanka.admin.app.AdminDeliveryConfigService;
import com.scanlanka.admin.app.AdminDeliveryConfigService.CourierRateRequest;
import com.scanlanka.admin.app.AdminDeliveryConfigService.CourierRateView;
import com.scanlanka.admin.app.AdminDeliveryConfigService.MethodRequest;
import com.scanlanka.admin.app.AdminDeliveryConfigService.MethodView;
import com.scanlanka.admin.app.AdminDeliveryConfigService.PostalZoneRequest;
import com.scanlanka.admin.app.AdminDeliveryConfigService.PostalZoneView;
import com.scanlanka.admin.app.AdminDeliveryConfigService.SettingsRequest;
import com.scanlanka.admin.app.AdminDeliveryConfigService.SettingsView;
import com.scanlanka.checkout.domain.CourierZone;
import com.scanlanka.checkout.domain.DeliveryMethod;
import com.scanlanka.shared.security.AuthPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Admin two-rail delivery config (08, 17). Under /api/admin/** → ADMIN-gated by SecurityConfig. */
@RestController
@RequestMapping("/api/admin")
public class AdminDeliveryConfigController {

    private final AdminDeliveryConfigService config;

    public AdminDeliveryConfigController(AdminDeliveryConfigService config) {
        this.config = config;
    }

    @GetMapping("/courier-rate-card")
    public List<CourierRateView> courierRates() {
        return config.listCourierRates();
    }

    @PutMapping("/courier-rate-card/{zone}")
    public CourierRateView upsertCourierRate(@PathVariable CourierZone zone, @RequestBody CourierRateRequest req,
                                             @AuthenticationPrincipal AuthPrincipal admin) {
        return config.upsertCourierRate(zone, req, adminId(admin));
    }

    @GetMapping("/delivery-settings")
    public SettingsView settings() {
        return config.getSettings();
    }

    @PutMapping("/delivery-settings")
    public SettingsView updateSettings(@RequestBody SettingsRequest req,
                                       @AuthenticationPrincipal AuthPrincipal admin) {
        return config.updateSettings(req, adminId(admin));
    }

    @GetMapping("/delivery-methods")
    public List<MethodView> methods() {
        return config.listMethods();
    }

    @PutMapping("/delivery-methods/{method}")
    public MethodView setMethod(@PathVariable DeliveryMethod method, @RequestBody MethodRequest req,
                                @AuthenticationPrincipal AuthPrincipal admin) {
        return config.setMethodEnabled(method, req, adminId(admin));
    }

    @GetMapping("/postal-zones/{postalCode}")
    public PostalZoneView postalZone(@PathVariable String postalCode) {
        return config.getPostalZone(postalCode);
    }

    @PutMapping("/postal-zones/{postalCode}")
    public PostalZoneView upsertPostalZone(@PathVariable String postalCode, @RequestBody PostalZoneRequest req,
                                           @AuthenticationPrincipal AuthPrincipal admin) {
        return config.upsertPostalZone(postalCode, req, adminId(admin));
    }

    @DeleteMapping("/postal-zones/{postalCode}")
    public void deletePostalZone(@PathVariable String postalCode, @AuthenticationPrincipal AuthPrincipal admin) {
        config.deletePostalZone(postalCode, adminId(admin));
    }

    private static Long adminId(AuthPrincipal admin) {
        return admin != null ? admin.userId() : null;
    }
}
