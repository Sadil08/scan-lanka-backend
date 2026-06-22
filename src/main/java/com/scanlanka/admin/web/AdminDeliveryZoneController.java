package com.scanlanka.admin.web;

import com.scanlanka.admin.app.AdminCheckoutConfigService;
import com.scanlanka.admin.app.DeliveryZoneAdminService;
import com.scanlanka.admin.app.DeliveryZoneAdminService.ZoneRequest;
import com.scanlanka.admin.app.DeliveryZoneAdminService.ZoneView;
import com.scanlanka.shared.security.AuthPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/delivery-zones")
public class AdminDeliveryZoneController {

    private final DeliveryZoneAdminService zones;

    public AdminDeliveryZoneController(DeliveryZoneAdminService zones) {
        this.zones = zones;
    }

    @GetMapping
    public List<ZoneView> list() {
        return zones.list();
    }

    @GetMapping("/{id}")
    public ZoneView get(@PathVariable long id) {
        return zones.get(id);
    }

    @PostMapping
    public ZoneView create(@RequestBody ZoneRequest req, @AuthenticationPrincipal AuthPrincipal admin) {
        return zones.create(req, adminId(admin));
    }

    @PutMapping("/{id}")
    public ZoneView update(@PathVariable long id, @RequestBody ZoneRequest req,
                           @AuthenticationPrincipal AuthPrincipal admin) {
        return zones.update(id, req, adminId(admin));
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable long id, @AuthenticationPrincipal AuthPrincipal admin) {
        zones.delete(id, adminId(admin));
    }

    private static Long adminId(AuthPrincipal admin) {
        return admin != null ? admin.userId() : null;
    }
}
