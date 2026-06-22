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

    public record SettingsView(boolean codEnabled, boolean bankTransferEnabled, String bankAccountDetails,
                               String whatsappLocal, String whatsappIntl) {}
    public record UpdateSettings(Boolean codEnabled, Boolean bankTransferEnabled, String bankAccountDetails,
                                 String whatsappLocal, String whatsappIntl) {}

    @GetMapping
    public SettingsView get() {
        return new SettingsView(
            settings.getBool("cod_enabled", true),
            settings.getBool("bank_transfer_enabled", true),
            settings.get("bank_account_details").orElse(""),
            settings.get("whatsapp_local").orElse(""),
            settings.get("whatsapp_intl").orElse(""));
    }

    @PutMapping
    public SettingsView update(@RequestBody UpdateSettings req, @AuthenticationPrincipal AuthPrincipal principal) {
        Long adminId = principal != null ? principal.userId() : null;
        if (req.codEnabled() != null) settings.put("cod_enabled", req.codEnabled().toString(), adminId);
        if (req.bankTransferEnabled() != null) {
            settings.put("bank_transfer_enabled", req.bankTransferEnabled().toString(), adminId);
        }
        if (req.bankAccountDetails() != null) {
            settings.put("bank_account_details", req.bankAccountDetails(), adminId);
        }
        if (req.whatsappLocal() != null) settings.put("whatsapp_local", req.whatsappLocal(), adminId);
        if (req.whatsappIntl() != null) settings.put("whatsapp_intl", req.whatsappIntl(), adminId);
        return get();
    }
}
