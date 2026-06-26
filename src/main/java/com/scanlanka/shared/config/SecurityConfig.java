package com.scanlanka.shared.config;

import com.scanlanka.auth.web.AdminTotpEnforcementFilter;
import com.scanlanka.auth.web.AuthFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.StaticHeadersWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Security baseline (global/02_SECURITY_BASELINE.md,
 * global/08_THREAT_MODEL.md).
 * Transport + header hardening + CORS allow-list. Real authN/authZ (JWT
 * httpOnly cookies, RLS GUC,
 * role gates) is added in Phase 1 (07-auth-accounts) — this is the
 * secure-by-default skeleton.
 */
@Configuration
public class SecurityConfig {

    private final List<String> allowedOrigins;

    // Constructor injection only (global/09 §5) — @Value on the parameter, not a
    // field.
    public SecurityConfig(@Value("${app.cors.allowed-origins}") List<String> allowedOrigins) {
        this.allowedOrigins = allowedOrigins;
    }

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http, AuthFilter authFilter,
            AdminTotpEnforcementFilter adminTotpFilter) throws Exception {
        http
                .cors(Customizer.withDefaults())
                // CSRF: relying on SameSite=Strict httpOnly cookies for our same-site SPA
                // (global/02 §7);
                // double-submit token is a later defense-in-depth add.
                .csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .headers(h -> h
                        .frameOptions(f -> f.deny()) // clickjacking (T-7)
                        .httpStrictTransportSecurity(hsts -> hsts // HTTPS/HSTS (T-13b)
                                // emit on every response: TLS is terminated at the proxy, so the app sees plain
                                // HTTP and Spring's default secure-only matcher would otherwise suppress HSTS.
                                .requestMatcher(
                                        org.springframework.security.web.util.matcher.AnyRequestMatcher.INSTANCE)
                                .includeSubDomains(true).maxAgeInSeconds(31_536_000))
                        .referrerPolicy(r -> r.policy(
                                org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER))
                        // Strict API CSP — backend serves JSON only (global/02 §7)
                        .addHeaderWriter(new StaticHeadersWriter("Content-Security-Policy",
                                "default-src 'none'; frame-ancestors 'none'; base-uri 'none'"))
                        .addHeaderWriter(new StaticHeadersWriter("X-Content-Type-Options", "nosniff"))
                        .addHeaderWriter(new StaticHeadersWriter("Permissions-Policy",
                                "camera=(), microphone=(), geolocation=()")))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
                        // Prometheus scrapers send no session cookie, so cookie-auth would 403 them. In
                        // prod
                        // management runs on an isolated internal port (application-prod.yml:
                        // management.server.port)
                        // that the load balancer never exposes — network isolation is the control here
                        // (P0-6).
                        .requestMatchers("/actuator/prometheus").permitAll()
                        .requestMatchers("/actuator/**").hasRole("ADMIN") // other metrics gated (global/04)
                        .requestMatchers("/api/ping", "/api/csp-report").permitAll()
                        .requestMatchers("/api/auth/**").permitAll() // public auth endpoints
                        .requestMatchers("/api/admin/**").hasRole("ADMIN") // role-gated server-side (SEC-ADMIN)
                        // Remaining endpoints are added per feature with their own rules; default
                        // permit for now.
                        .anyRequest().permitAll())
                .addFilterBefore(authFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(adminTotpFilter, AuthFilter.class);
        return http.build();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration c = new CorsConfiguration();
        c.setAllowedOrigins(allowedOrigins); // allow-list only — no wildcard (global/02 §7)
        c.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        c.setAllowedHeaders(List.of(
            HttpHeaders.CONTENT_TYPE,
            HttpHeaders.AUTHORIZATION,
            "X-Captcha-Token"));
        c.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource src = new UrlBasedCorsConfigurationSource();
        src.registerCorsConfiguration("/**", c);
        return src;
    }
}
