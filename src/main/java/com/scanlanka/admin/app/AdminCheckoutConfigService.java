package com.scanlanka.admin.app;

import com.scanlanka.checkout.domain.DeliveryConfig;
import com.scanlanka.checkout.domain.TaxConfig;
import com.scanlanka.checkout.infra.DeliveryConfigRepository;
import com.scanlanka.checkout.infra.TaxConfigRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Admin delivery-engine + tax config (08 FR-ADMIN-1/3). Applies to new checkouts only. */
@Service
public class AdminCheckoutConfigService {

    private final DeliveryConfigRepository deliveryConfigs;
    private final TaxConfigRepository taxConfigs;
    private final AuditService audit;

    public AdminCheckoutConfigService(DeliveryConfigRepository deliveryConfigs, TaxConfigRepository taxConfigs,
                                      AuditService audit) {
        this.deliveryConfigs = deliveryConfigs;
        this.taxConfigs = taxConfigs;
        this.audit = audit;
    }

    public record DeliveryConfigView(long pickFirstCents, long pickNextCents, long fragileSurchargeCents,
                                     long oversizeSurchargeCents, int dimDivisor) {}
    public record TaxConfigView(int rateBps, String label) {}

    @Transactional(readOnly = true)
    public DeliveryConfigView deliveryConfig() {
        return toDelivery(loadDelivery());
    }

    @Transactional(readOnly = true)
    public TaxConfigView taxConfig() {
        return toTax(loadTax());
    }

    @Transactional
    public DeliveryConfigView updateDelivery(DeliveryConfigView req, Long adminId) {
        DeliveryConfig c = loadDelivery();
        String before = String.valueOf(c.getPickFirstCents());
        c.update(req.pickFirstCents(), req.pickNextCents(), req.fragileSurchargeCents(),
            req.oversizeSurchargeCents(), req.dimDivisor());
        deliveryConfigs.save(c);
        audit.log(adminId, "DELIVERY_CONFIG", "delivery_config", "1", before, String.valueOf(c.getPickFirstCents()));
        return toDelivery(c);
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

    private DeliveryConfig loadDelivery() {
        return deliveryConfigs.findById(1)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Config missing"));
    }

    private TaxConfig loadTax() {
        return taxConfigs.findById(1)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tax missing"));
    }

    private static DeliveryConfigView toDelivery(DeliveryConfig c) {
        return new DeliveryConfigView(c.getPickFirstCents(), c.getPickNextCents(), c.getFragileSurchargeCents(),
            c.getOversizeSurchargeCents(), c.getDimDivisor());
    }

    private static TaxConfigView toTax(TaxConfig t) {
        return new TaxConfigView(t.getRateBps(), t.getLabel());
    }
}
