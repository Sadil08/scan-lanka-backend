package com.scanlanka.checkout.web;

import com.scanlanka.checkout.app.DeliveryQueryService;
import com.scanlanka.checkout.app.DeliveryQueryService.PostalCodeView;
import com.scanlanka.checkout.app.DeliveryQueryService.ZoneLocationView;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Public delivery lookups (05 §3). */
@RestController
@RequestMapping("/api/delivery")
public class DeliveryController {

    private final DeliveryQueryService delivery;

    public DeliveryController(DeliveryQueryService delivery) {
        this.delivery = delivery;
    }

    @GetMapping("/postal-codes")
    public List<PostalCodeView> postalCodes(@RequestParam(required = false) String q) {
        return delivery.postalCodes(q);
    }

    @GetMapping("/locations")
    public List<ZoneLocationView> locations() {
        return delivery.locations();
    }
}
