package com.scanlanka.checkout.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/** Checkout request DTOs (05). Guest-friendly; totals are server-computed (SEC-PAY). */
public final class CheckoutRequests {

    private CheckoutRequests() {}

    public record ItemDTO(@NotNull Long productId, Long variantId, @Min(1) int quantity) {}

    public record AddressDTO(String street, String city, String province, String postalCode) {}

    public record BillingDTO(String name, String taxId, String street, String city, String province, String postalCode) {}

    /** All available rails for a cart + postal code (powers the rail picker). */
    public record OptionsRequest(List<ItemDTO> items, String postalCode, String city) {}

    public record QuoteRequest(List<ItemDTO> items, @NotBlank String deliveryMethod, String postalCode, String city) {}

    public record PlaceRequest(
        List<ItemDTO> items,
        @NotBlank String deliveryMethod,
        String paymentChoice,                 // ONLINE | COD; null ⇒ ONLINE (lorry). Courier is always COD.
        String paymentMethod,                 // CARD | BANK when paying online; ignored for COD
        AddressDTO ship,
        BillingDTO billing,
        @NotBlank String contactName,
        @NotBlank String contactPhone,
        @Email @NotBlank String contactEmail,
        String guestEmail) {}
}
