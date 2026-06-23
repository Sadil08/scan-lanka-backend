package com.scanlanka.geo.web;

import com.scanlanka.geo.app.CurrencyService;
import com.scanlanka.geo.app.GeoService;
import com.scanlanka.shared.security.ClientIp;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class GeoController {

    private final GeoService geo;
    private final CurrencyService currency;

    public GeoController(GeoService geo, CurrencyService currency) {
        this.geo = geo;
        this.currency = currency;
    }

    public record GeoView(String country, String currency, boolean isSriLanka,
                          boolean canCheckout, String whatsappNumber, boolean indicativePricing) {}

    public record FxRatesView(String base, Map<String, ?> rates) {}

    @GetMapping("/geo")
    public GeoView geo(@RequestParam(required = false) String country, HttpServletRequest req) {
        var ctx = geo.resolve(ClientIp.from(req), country);
        return new GeoView(ctx.country(), ctx.currency(), ctx.isSriLanka(), ctx.canCheckout(),
            ctx.whatsappNumber(), !ctx.isSriLanka());
    }

    @GetMapping("/fx-rates")
    public FxRatesView fxRates() {
        return new FxRatesView("LKR", currency.allRates());
    }
}
