package ch.batbern.shared.security;

import ch.batbern.shared.test.security.TestTokens;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;

import java.security.interfaces.RSAPublicKey;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The time rules of {@link AgentTokenValidator} on a fixed clock (agentic interaction, AD-3): the
 * lifetime cap, the clock skew for {@code iat} and {@code iat} strictly before {@code exp}, the way
 * {@link AgentTokenSigner} mints.
 */
@DisplayName("AgentTokenValidator")
class AgentTokenValidatorTest {

    private static final Instant NOW = Instant.now().truncatedTo(ChronoUnit.SECONDS);
    private static final AgentTokenValidator VALIDATOR =
            new AgentTokenValidator(Clock.fixed(NOW, ZoneOffset.UTC));

    private static Jwt token(Instant issuedAt, Instant expiresAt) {
        return Jwt.withTokenValue("agent-token")
                .header("alg", "RS256")
                .issuer(BatbernJwtDecoder.AGENT_ISSUER)
                .subject("agent-sub")
                .claim("custom:username", "agent.mail")
                .claim("act", Map.of("run", "run-1", "principal", "organizer"))
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .build();
    }

    private static void assertRefused(OAuth2TokenValidatorResult result, String description) {
        assertThat(result.hasErrors()).isTrue();
        assertThat(result.getErrors()).extracting(OAuth2Error::getErrorCode)
                .containsOnly(OAuth2ErrorCodes.INVALID_TOKEN);
        assertThat(result.getErrors()).extracting(OAuth2Error::getDescription)
                .anySatisfy(d -> assertThat(d).contains(description));
    }

    /**
     * {@code iat == exp} cannot be built as a Spring {@link Jwt} (its constructor asserts
     * {@code exp} after {@code iat}), so the case runs through the shared decoder, as a service sees it.
     */
    @Test
    void should_refuse_when_iatEqualsExp() {
        Instant now = Instant.now();
        String token = TestTokens.agent("s", "u", "r", "organizer").claims(c -> c
                .issueTime(Date.from(now.plusSeconds(30))).expirationTime(Date.from(now.plusSeconds(30)))).build();
        BatbernJwtDecoder decoder = BatbernJwtDecoder.builder()
                .agent((RSAPublicKey) TestTokens.agentKeyPair().getPublic())
                .build();

        assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtException.class);
        // Positive control: the same decoder accepts the token one second longer.
        String valid = TestTokens.agent("s", "u", "r", "organizer").claims(c -> c
                .issueTime(Date.from(now.plusSeconds(30))).expirationTime(Date.from(now.plusSeconds(31)))).build();
        assertThat(decoder.decode(valid).getSubject()).isEqualTo("s");
    }

    @Test
    void should_accept_when_iatAtClockSkewEdge() {
        Instant iat = NOW.plus(AgentTokenValidator.CLOCK_SKEW);
        assertThat(VALIDATOR.validate(token(iat, iat.plusSeconds(300))).hasErrors()).isFalse();
    }

    @Test
    void should_refuse_when_iatBeyondClockSkew() {
        Instant iat = NOW.plus(AgentTokenValidator.CLOCK_SKEW).plusSeconds(1);
        assertRefused(VALIDATOR.validate(token(iat, iat.plusSeconds(300))), "iat in the future");
    }

    @Test
    void should_accept_when_lifetimeIsMaximum() {
        assertThat(VALIDATOR.validate(token(NOW, NOW.plusSeconds(300))).hasErrors()).isFalse();
    }

    @Test
    void should_refuse_when_lifetimeExceedsMaximum() {
        assertRefused(VALIDATOR.validate(token(NOW, NOW.plusSeconds(301))), "lives longer than 300 s");
    }
}
