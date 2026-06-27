package com.scanlanka.admin.web;

import com.scanlanka.admin.app.AdminCheckoutConfigService;
import com.scanlanka.admin.app.AdminCheckoutConfigService.TaxConfigView;
import com.scanlanka.shared.security.AuthPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
public class AdminCheckoutConfigController {

    private final AdminCheckoutConfigService config;

    public AdminCheckoutConfigController(AdminCheckoutConfigService config) {
        this.config = config;
    }

    @GetMapping("/tax-config")
    public TaxConfigView taxConfig() {
        return config.taxConfig();
    }

    @PutMapping("/tax-config")
    public TaxConfigView updateTax(@RequestBody TaxConfigView req,
                                   @AuthenticationPrincipal AuthPrincipal admin) {
        return config.updateTax(req, admin != null ? admin.userId() : null);
    }
}
