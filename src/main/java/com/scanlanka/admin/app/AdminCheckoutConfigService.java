package com.scanlanka.admin.app;

import com.scanlanka.checkout.domain.PayHereFeeConfig;
import com.scanlanka.checkout.domain.TaxConfig;
import com.scanlanka.checkout.infra.PayHereFeeConfigRepository;
import com.scanlanka.checkout.infra.TaxConfigRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Admin tax + PayHere-fee config (08 FR-ADMIN-3). Applies to new checkouts only. The old DIM/pick-pack
 * delivery config was retired with the two-rail model (17); delivery config now lives in
 * `AdminDeliveryConfigService`. The PayHere fee is a separate, independently admin-tunable surcharge on
 * prepaid CARD checkouts only (loosely coupled to tax — its own row, its own endpoint).
 */
@Service
public class AdminCheckoutConfigService {

    private final TaxConfigRepository taxConfigs;
    private final PayHereFeeConfigRepository payHereFeeConfigs;
    private final AuditService audit;

    public AdminCheckoutConfigService(TaxConfigRepository taxConfigs,
                                      PayHereFeeConfigRepository payHereFeeConfigs, AuditService audit) {
        this.taxConfigs = taxConfigs;
        this.payHereFeeConfigs = payHereFeeConfigs;
        this.audit = audit;
    }

    public record TaxConfigView(int rateBps, String label) {}
    public record PayHereFeeConfigView(int rateBps, String label) {}

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

    @Transactional(readOnly = true)
    public PayHereFeeConfigView payHereFeeConfig() {
        return toPayHereFee(loadPayHereFee());
    }

    @Transactional
    public PayHereFeeConfigView updatePayHereFee(PayHereFeeConfigView req, Long adminId) {
        PayHereFeeConfig f = loadPayHereFee();
        String before = f.getRateBps() + "bps";
        f.update(req.rateBps(), req.label());
        payHereFeeConfigs.save(f);
        audit.log(adminId, "PAYHERE_FEE_CONFIG", "payhere_fee_config", "1", before, f.getRateBps() + "bps");
        return toPayHereFee(f);
    }

    private TaxConfig loadTax() {
        return taxConfigs.findById(1)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tax missing"));
    }

    private PayHereFeeConfig loadPayHereFee() {
        return payHereFeeConfigs.findById(1)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "PayHere fee config missing"));
    }

    private static TaxConfigView toTax(TaxConfig t) {
        return new TaxConfigView(t.getRateBps(), t.getLabel());
    }

    private static PayHereFeeConfigView toPayHereFee(PayHereFeeConfig f) {
        return new PayHereFeeConfigView(f.getRateBps(), f.getLabel());
    }
}
