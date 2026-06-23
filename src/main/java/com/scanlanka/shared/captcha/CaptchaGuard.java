package com.scanlanka.shared.captcha;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/** Lightweight CAPTCHA gate — shared secret header (12/11 T-18). */
@Component
public class CaptchaGuard {

    private final String secret;

    public CaptchaGuard(@Value("${app.captcha.secret:test-captcha-bypass}") String secret) {
        this.secret = secret;
    }

    public void verify(String token) {
        if (token == null || token.isBlank() || !secret.equals(token.trim())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "CAPTCHA_REQUIRED");
        }
    }
}
