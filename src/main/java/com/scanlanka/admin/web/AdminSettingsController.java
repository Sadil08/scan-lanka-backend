package com.scanlanka.admin.web;

import com.scanlanka.admin.app.SettingsService;
import com.scanlanka.shared.security.AuthPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Admin store settings (08 FR-ADMIN-4). Under /api/admin/** → ADMIN-gated. */
@RestController
@RequestMapping("/api/admin/settings")
public class AdminSettingsController {

    private final SettingsService settings;

    public AdminSettingsController(SettingsService settings) {
        this.settings = settings;
    }

    public record SettingsView(boolean codEnabled, boolean bankTransferEnabled) {}
    public record UpdateSettings(Boolean codEnabled, Boolean bankTransferEnabled) {}

    @GetMapping
    public SettingsView get() {
        return new SettingsView(
            settings.getBool("cod_enabled", true),
            settings.getBool("bank_transfer_enabled", true));
    }

    @PutMapping
    public SettingsView update(@RequestBody UpdateSettings req, @AuthenticationPrincipal AuthPrincipal principal) {
        Long adminId = principal != null ? principal.userId() : null;
        if (req.codEnabled() != null) settings.put("cod_enabled", req.codEnabled().toString(), adminId);
        if (req.bankTransferEnabled() != null) {
            settings.put("bank_transfer_enabled", req.bankTransferEnabled().toString(), adminId);
        }
        return get();
    }
}
