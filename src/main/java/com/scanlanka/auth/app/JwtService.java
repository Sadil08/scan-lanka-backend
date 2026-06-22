package com.scanlanka.auth.app;

import com.scanlanka.auth.domain.AppUser;
import com.scanlanka.auth.domain.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

/**
 * Short-lived access token in an httpOnly cookie (07 FR-AUTH-3/12). Validates signature + iss + aud +
 * exp + token_version every request (global/02 §2). HS256.
 */
@Service
public class JwtService {

    private final AuthProperties props;
    private final SecretKey key;

    public JwtService(AuthProperties props) {
        this.props = props;
        this.key = Keys.hmacShaKeyFor(props.jwtSecret().getBytes(StandardCharsets.UTF_8));
    }

    /** Claims carried in the access token. */
    public record AccessClaims(long userId, Role role, int tokenVersion) {}

    public String issueAccessToken(AppUser user) {
        Instant now = Instant.now();
        return Jwts.builder()
            .subject(String.valueOf(user.getId()))
            .issuer(props.issuer())
            .audience().add(props.audience()).and()
            .claim("role", user.getRole().name())
            .claim("tv", user.getTokenVersion())
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plusSeconds(props.accessTtlMinutes() * 60)))
            .signWith(key)
            .compact();
    }

    /** @throws InvalidTokenException on any signature/issuer/audience/expiry failure. */
    public AccessClaims parse(String token) {
        try {
            Claims c = Jwts.parser()
                .verifyWith(key)
                .requireIssuer(props.issuer())
                .requireAudience(props.audience())
                .build()
                .parseSignedClaims(token)
                .getPayload();
            return new AccessClaims(
                Long.parseLong(c.getSubject()),
                Role.valueOf(c.get("role", String.class)),
                c.get("tv", Integer.class));
        } catch (JwtException | IllegalArgumentException e) {
            throw new InvalidTokenException("invalid token");
        }
    }

    public static class InvalidTokenException extends RuntimeException {
        public InvalidTokenException(String message) {
            super(message);
        }
    }
}
