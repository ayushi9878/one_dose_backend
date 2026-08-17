package com.careflow.security;

import com.careflow.config.CareFlowProperties;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("JWT token provider")
class JwtTokenProviderTest {

    private JwtTokenProvider providerWithSecret(String secret) {
        CareFlowProperties properties = new CareFlowProperties();
        properties.getSecurity().getJwt().setSecret(secret);
        properties.getSecurity().getJwt().setExpirationMinutes(60);
        return new JwtTokenProvider(properties);
    }

    @Test
    @DisplayName("a raw passphrase containing non-base64 characters is accepted")
    void acceptsRawPassphraseWithHyphens() {
        JwtTokenProvider provider =
                providerWithSecret("test-secret-key-for-careflow-that-is-long-enough");

        String token = provider.generateToken(1L, "a@b.test", "Test User", "CARE_MANAGER");

        assertThat(provider.isValid(token)).isTrue();
    }

    @Test
    @DisplayName("a base64-encoded secret is accepted")
    void acceptsBase64Secret() {
        String secret = Base64.getEncoder()
                .encodeToString("a-32-byte-or-longer-secret-value!!".getBytes());
        JwtTokenProvider provider = providerWithSecret(secret);

        String token = provider.generateToken(1L, "a@b.test", "Test User", "ADMIN");

        assertThat(provider.isValid(token)).isTrue();
    }

    @Test
    @DisplayName("a secret with too little key material is rejected at startup")
    void rejectsShortSecret() {
        assertThatThrownBy(() -> providerWithSecret("too-short"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32 bytes");
    }

    @Test
    @DisplayName("the token carries the user id, role and expiry")
    void tokenCarriesIdentityClaims() {
        JwtTokenProvider provider =
                providerWithSecret("test-secret-key-for-careflow-that-is-long-enough");

        String token = provider.generateToken(42L, "rina@example.com", "Rina Mehta", "PATIENT");
        Claims claims = provider.parseClaims(token);

        assertThat(claims.getSubject()).isEqualTo("42");
        assertThat(claims.get("role")).isEqualTo("PATIENT");
        assertThat(claims.getExpiration()).isAfter(claims.getIssuedAt());
    }

    @Test
    @DisplayName("a token signed with a different secret is rejected")
    void rejectsTokenFromAnotherSecret() {
        JwtTokenProvider issuer =
                providerWithSecret("first-secret-key-for-careflow-long-enough-value");
        JwtTokenProvider verifier =
                providerWithSecret("second-secret-key-for-careflow-long-enough-val");

        String token = issuer.generateToken(1L, "a@b.test", "Test User", "ADMIN");

        assertThat(verifier.isValid(token)).isFalse();
    }

    @Test
    @DisplayName("a token whose payload was altered fails signature verification")
    void rejectsTamperedPayload() {
        JwtTokenProvider provider =
                providerWithSecret("test-secret-key-for-careflow-that-is-long-enough");
        String token = provider.generateToken(1L, "a@b.test", "Test User", "CARE_MANAGER");

        String[] parts = token.split("\\.");
        String forgedPayload = Base64.getUrlEncoder().withoutPadding().encodeToString(
                new String(Base64.getUrlDecoder().decode(parts[1]))
                        .replace("\"CARE_MANAGER\"", "\"ADMIN\"")
                        .getBytes());
        String forged = parts[0] + "." + forgedPayload + "." + parts[2];

        assertThat(provider.isValid(forged)).isFalse();
    }

    @Test
    @DisplayName("a structurally invalid token is rejected")
    void rejectsMalformedToken() {
        JwtTokenProvider provider =
                providerWithSecret("test-secret-key-for-careflow-that-is-long-enough");

        assertThat(provider.isValid("not.a.jwt")).isFalse();
    }
}
