package com.scanlanka.admin.app;

import com.scanlanka.checkout.domain.TaxConfig;
import com.scanlanka.checkout.infra.TaxConfigRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Admin tax config (08 FR-ADMIN-3). Applies to new checkouts only. The old DIM/pick-pack delivery
 * config was retired with the two-rail model (17); delivery config now lives in `AdminDeliveryConfigService`.
 */
@Service
public class AdminCheckoutConfigService {

    private final TaxConfigRepository taxConfigs;
    private final AuditService audit;

    public AdminCheckoutConfigService(TaxConfigRepository taxConfigs, AuditService audit) {
        this.taxConfigs = taxConfigs;
        this.audit = audit;
    }

    public record TaxConfigView(int rateBps, String label) {}

    @Transactional(readOnly = true)
    public TaxConfigView taxConfig() {
        return toTax(loadTax());
    }

    @Transactional
    public TaxConfigView updateTax(TaxConfigView req, Long adminId) {
        TaxConfig t = loadTax();
        String before = t.getRateBps() + "bps";
        t.update(req.rateBps(), req.label());
        taxConfigs.save(t);
        audit.log(adminId, "TAX_CONFIG", "tax_config", "1", before, t.getRateBps() + "bps");
        return toTax(t);
    }

    private TaxConfig loadTax() {
        return taxConfigs.findById(1)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tax missing"));
    }

    private static TaxConfigView toTax(TaxConfig t) {
        return new TaxConfigView(t.getRateBps(), t.getLabel());
    }
}
