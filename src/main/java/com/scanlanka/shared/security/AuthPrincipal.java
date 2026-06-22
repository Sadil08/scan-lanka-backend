package com.scanlanka.shared.security;

/**
 * The authenticated principal set by AuthFilter from the access token (global/02 §2).
 * Role kept as String so shared/ stays decoupled from feature packages (global/09 §3).
 */
public record AuthPrincipal(long userId, String role) {
}
