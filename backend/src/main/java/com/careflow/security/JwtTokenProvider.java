package com.careflow.security;

import com.careflow.config.CareFlowProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.DecodingException;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

@Slf4j
@Component
public class JwtTokenProvider {

    private static final String CLAIM_ROLE = "role";
    private static final String CLAIM_NAME = "name";

    private final SecretKey signingKey;
    private final long expirationMinutes;
    private final String issuer;

    public JwtTokenProvider(CareFlowProperties properties) {
        CareFlowProperties.Jwt jwt = properties.getSecurity().getJwt();
        this.signingKey = buildKey(jwt.getSecret());
        this.expirationMinutes = jwt.getExpirationMinutes();
        this.issuer = jwt.getIssuer();
    }

    /**
     * Accepts either a base64-encoded secret or a raw passphrase. HS256 requires
     * at least 256 bits of key material, so short secrets fail fast at startup
     * rather than silently weakening every token.
     */
    private static SecretKey buildKey(String secret) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("JWT_SECRET must be configured.");
        }

        byte[] keyBytes;
        try {
            keyBytes = Decoders.BASE64.decode(secret);
        } catch (DecodingException | IllegalArgumentException notBase64) {
            keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        }

        // A short base64 payload usually means the value was meant as a raw
        // passphrase, so prefer its literal bytes before rejecting it outright.
        if (keyBytes.length < 32) {
            byte[] rawBytes = secret.getBytes(StandardCharsets.UTF_8);
            if (rawBytes.length >= 32) {
                keyBytes = rawBytes;
            }
        }

        if (keyBytes.length < 32) {
            throw new IllegalStateException(
                    "JWT_SECRET must provide at least 32 bytes of key material for HS256.");
        }
        return Keys.hmacShaKeyFor(keyBytes);
    }

    public String generateToken(Long userId, String email, String fullName, String role) {
        Instant now = Instant.now();
        Instant expiry = now.plusSeconds(expirationMinutes * 60);
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .issuer(issuer)
                .claim(CLAIM_ROLE, role)
                .claim(CLAIM_NAME, fullName)
                .claim("email", email)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(signingKey)
                .compact();
    }

    public long getExpirationSeconds() {
        return expirationMinutes * 60;
    }

    public Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .requireIssuer(issuer)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public boolean isValid(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException ex) {
            log.debug("Rejected JWT: {}", ex.getMessage());
            return false;
        }
    }
}
