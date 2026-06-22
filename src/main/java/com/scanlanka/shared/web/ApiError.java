package com.scanlanka.shared.web;

/** Uniform error shape returned to clients (global/02 §4, global/09 §4). No stack traces / entity details. */
public record ApiError(String code, String message) {
}
