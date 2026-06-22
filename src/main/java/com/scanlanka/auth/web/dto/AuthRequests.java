package com.scanlanka.auth.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Request DTOs for auth endpoints. Strict input typing/validation (global/02 §4). No role/id fields. */
public final class AuthRequests {

    private AuthRequests() {}

    public record RegisterRequest(
        @Email @NotBlank String email,
        @NotBlank @Size(min = 8, max = 100) String password,
        @Size(max = 160) String name) {}

    public record VerifyEmailRequest(
        @Email @NotBlank String email,
        @NotBlank @Size(min = 6, max = 6) String code) {}

    public record LoginRequest(
        @Email @NotBlank String email,
        @NotBlank String password,
        String totp) {}

    public record ForgotRequest(@Email @NotBlank String email) {}

    public record ResetRequest(
        @Email @NotBlank String email,
        @NotBlank @Size(min = 6, max = 6) String code,
        @NotBlank @Size(min = 8, max = 100) String newPassword) {}
}
