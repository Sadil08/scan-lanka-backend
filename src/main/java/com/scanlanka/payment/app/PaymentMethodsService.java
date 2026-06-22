package com.scanlanka.payment.app;

import com.scanlanka.admin.app.SettingsService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Public payment method toggles (06 FR-PAY-8). */
@Service
@Transactional(readOnly = true)
public class PaymentMethodsService {

    private final SettingsService settings;
    private final PayHereProperties payhere;

    public PaymentMethodsService(SettingsService settings, PayHereProperties payhere) {
        this.settings = settings;
        this.payhere = payhere;
    }

    public record PaymentMethodsView(boolean payhere, boolean bankTransfer, boolean deliveryCod) {}

    public PaymentMethodsView methods() {
        boolean payhereEnabled = payhere.merchantId() != null && !payhere.merchantId().isBlank();
        return new PaymentMethodsView(
            payhereEnabled,
            settings.getBool("bank_transfer_enabled", true),
            settings.getBool("cod_enabled", true));
    }
}
