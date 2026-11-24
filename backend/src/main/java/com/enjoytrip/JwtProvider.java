package com.enjoytrip;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Date;
import java.util.Optional;

/** Issues and verifies signed access tokens (HS256). */
@Component
public class JwtProvider {
    private static final Logger log = LoggerFactory.getLogger(JwtProvider.class);
    private final SecretKey key;
    final long accessExpirySeconds;
    final long refreshExpirySeconds;
    final boolean cookieSecure;

    JwtProvider(@Value("${app.jwt.secret:}") String secret,
                @Value("${app.jwt.access-expiry-seconds:1800}") long accessExpirySeconds,
                @Value("${app.jwt.refresh-expiry-seconds:1209600}") long refreshExpirySeconds,
                @Value("${app.jwt.cookie-secure:false}") boolean cookieSecure) {
        byte[] bytes;
        if (secret == null || secret.isBlank()) {
            log.warn("JWT_SECRET is not set; using a random key. Issued tokens become invalid after restart.");
            bytes = new byte[32]; new SecureRandom().nextBytes(bytes);
        } else {
            bytes = secret.getBytes(StandardCharsets.UTF_8);
            if (bytes.length < 32) throw new IllegalStateException("app.jwt.secret must be at least 32 bytes");
        }
        this.key = Keys.hmacShaKeyFor(bytes);
        this.accessExpirySeconds = accessExpirySeconds;
        this.refreshExpirySeconds = refreshExpirySeconds;
        this.cookieSecure = cookieSecure;
    }

    public String createAccessToken(Member member) {
        long now = System.currentTimeMillis();
        return Jwts.builder().subject(member.id).claim("cv", member.credentialVersion)
            .issuedAt(new Date(now)).expiration(new Date(now + accessExpirySeconds * 1000))
            .signWith(key).compact();
    }

    /** Returns the principal for a valid, unexpired token; empty for anything else. */
    public Optional<AuthPrincipal> parse(String token) {
        try {
            Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
            Number cv = claims.get("cv", Number.class);
            if (claims.getSubject() == null || cv == null) return Optional.empty();
            return Optional.of(new AuthPrincipal(claims.getSubject(), cv.longValue()));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
