package com.scanlanka.checkout.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public final class AddressRequests {

    private AddressRequests() {}

    public record AddressInput(
        String label,
        @NotBlank String street,
        @NotBlank String city,
        @NotBlank String province,
        @NotBlank String postalCode,
        @NotBlank String phone,
        @Email @NotBlank String email,
        boolean isDefault) {}

    public record AddressView(
        long id, String label, String street, String city, String province,
        String postalCode, String phone, String email, boolean isDefault) {}
}
