package com.scanlanka.shared.security;

import jakarta.servlet.http.HttpServletRequest;

public final class ClientIp {

    private ClientIp() {}

    public static String from(HttpServletRequest request) {
        String fwd = request.getHeader("X-Forwarded-For");
        if (fwd != null && !fwd.isBlank()) return fwd.split(",")[0].trim();
        String test = request.getHeader("X-Test-Country-Ip");
        if (test != null && !test.isBlank()) return test;
        return request.getRemoteAddr();
    }
}
