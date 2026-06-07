package ch.batbern.companyuser.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link AdditionalEmailVerificationTokenService}. A fixed,
 * &gt;=256-bit secret is used so generate/validate share the same signing key
 * (the {@code "changeme"} sentinel would generate a random key per instance).
 */
@DisplayName("AdditionalEmailVerificationTokenService")
class AdditionalEmailVerificationTokenServiceTest {

    private static final String SECRET = "test-secret-key-that-is-long-enough-for-hs256-aaaa";

    @Test
    @DisplayName("should_roundTripClaims_when_validToken")
    void should_roundTripClaims_when_validToken() {
        AdditionalEmailVerificationTokenService service =
                new AdditionalEmailVerificationTokenService(SECRET, 48L);
        UUID id = UUID.randomUUID();

        String token = service.generateToken(id, "box@example.com");
        Claims claims = service.validateToken(token);

        assertThat(service.getAdditionalEmailId(claims)).isEqualTo(id);
        assertThat(service.getEmail(claims)).isEqualTo("box@example.com");
    }

    @Test
    @DisplayName("should_throwExpired_when_tokenPastTtl")
    void should_throwExpired_when_tokenPastTtl() {
        AdditionalEmailVerificationTokenService service =
                new AdditionalEmailVerificationTokenService(SECRET, 0L);
        String token = service.generateToken(UUID.randomUUID(), "box@example.com");

        assertThatThrownBy(() -> service.validateToken(token))
                .isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    @DisplayName("should_throwJwt_when_signatureFromDifferentKey")
    void should_throwJwt_when_signatureFromDifferentKey() {
        AdditionalEmailVerificationTokenService issuer =
                new AdditionalEmailVerificationTokenService(SECRET, 48L);
        AdditionalEmailVerificationTokenService verifier =
                new AdditionalEmailVerificationTokenService(
                        "another-secret-key-that-is-also-long-enough-bbbb", 48L);
        String token = issuer.generateToken(UUID.randomUUID(), "box@example.com");

        assertThatThrownBy(() -> verifier.validateToken(token))
                .isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("should_throwIllegalArgument_when_malformedToken")
    void should_throwIllegalArgument_when_malformedToken() {
        AdditionalEmailVerificationTokenService service =
                new AdditionalEmailVerificationTokenService(SECRET, 48L);

        assertThatThrownBy(() -> service.validateToken("not.a.jwt"))
                .isInstanceOf(RuntimeException.class);
    }
}
