package com.scanlanka.checkout.web;

import com.scanlanka.checkout.app.CheckoutService;
import com.scanlanka.checkout.app.CheckoutService.ItemInput;
import com.scanlanka.checkout.app.CheckoutService.PlaceInput;
import com.scanlanka.checkout.web.dto.CheckoutRequests.ItemDTO;
import com.scanlanka.checkout.web.dto.CheckoutRequests.PlaceRequest;
import com.scanlanka.checkout.web.dto.CheckoutRequests.QuoteRequest;
import com.scanlanka.order.app.OrderCommands;
import com.scanlanka.order.domain.DeliveryPayment;
import com.scanlanka.order.domain.FulfilmentType;
import com.scanlanka.shared.security.AuthPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/** Checkout API (05 §3). Public — guests can quote + place orders. Identity from token if present. */
@RestController
@RequestMapping("/api/checkout")
public class CheckoutController {

    private final CheckoutService checkout;

    public CheckoutController(CheckoutService checkout) {
        this.checkout = checkout;
    }

    @PostMapping("/quote")
    public CheckoutService.Quote quote(@Valid @RequestBody QuoteRequest req) {
        return checkout.quote(toItems(req.items()), fulfilment(req.fulfilmentType()),
            req.postalCode(), deliveryPayment(req.deliveryPayment()));
    }

    @PostMapping
    public CheckoutService.Placed place(@Valid @RequestBody PlaceRequest req,
                                        @AuthenticationPrincipal AuthPrincipal principal) {
        Long customerId = principal != null ? principal.userId() : null;
        OrderCommands.Address ship = req.ship() == null ? null
            : new OrderCommands.Address(req.ship().street(), req.ship().city(), req.ship().province(), req.ship().postalCode());
        OrderCommands.Billing billing = req.billing() == null ? null
            : new OrderCommands.Billing(req.billing().name(), req.billing().taxId(), req.billing().street(),
                req.billing().city(), req.billing().province(), req.billing().postalCode());

        PlaceInput input = new PlaceInput(toItems(req.items()), fulfilment(req.fulfilmentType()),
            deliveryPayment(req.deliveryPayment()), ship, billing,
            req.contactName(), req.contactPhone(), req.contactEmail(),
            customerId, customerId == null ? req.contactEmail() : null);
        return checkout.place(input);
    }

    private static List<ItemInput> toItems(List<ItemDTO> items) {
        if (items == null) return List.of();
        return items.stream().map(i -> new ItemInput(i.productId(), i.variantId(), i.quantity())).toList();
    }

    private static FulfilmentType fulfilment(String v) {
        try {
            return FulfilmentType.valueOf(v);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_FULFILMENT_TYPE");
        }
    }

    private static DeliveryPayment deliveryPayment(String v) {
        try {
            return DeliveryPayment.valueOf(v);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_DELIVERY_PAYMENT");
        }
    }
}
