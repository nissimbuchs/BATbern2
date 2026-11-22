package ch.batbern.shared.security;

import ch.batbern.shared.test.security.TestTokens;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The audience guard (AD-15, story 7): a token carrying {@code act} may not send to an audience;
 * a person, a mock user and a call without a user pass.
 */
@DisplayName("AudienceGuard")
class AudienceGuardTest {

    private static final String SUB = "0d6f4c1e-agent-sub";
    private static final String USERNAME = "hong.an";

    private static final BatbernJwtDecoder DECODER = BatbernJwtDecoder.builder()
            .agent((RSAPublicKey) TestTokens.agentKeyPair().getPublic())
            .build();

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private static void signIn(Authentication authentication) {
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    @Test
    void should_refuseWithOperationNamed_when_agentTokenSignedIn() {
        signIn(new JwtAuthenticationToken(
                DECODER.decode(TestTokens.agentToken(SUB, USERNAME, "run-7f3a", "organizer"))));

        assertThatThrownBy(() -> AudienceGuard.refuseAgentTokens("mail-center send"))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("Audience sends refuse agent tokens (AD-15): mail-center send")
                .hasMessageNotContaining(USERNAME);
    }

    @Test
    void should_refuse_when_actCarriesNoRun() {
        Jwt jwt = Jwt.withTokenValue("t").header("alg", "RS256").subject(SUB)
                .claim("custom:username", USERNAME)
                .claim("act", Map.of("principal", "organizer"))
                .build();
        signIn(new JwtAuthenticationToken(jwt));

        assertThatThrownBy(() -> AudienceGuard.refuseAgentTokens("partner meeting invite"))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("partner meeting invite");
    }

    @Test
    void should_pass_when_cognitoTokenSignedIn() {
        Jwt jwt = Jwt.withTokenValue("cognito").header("alg", "RS256")
                .subject("cognito-sub").issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(600))
                .claim("custom:username", USERNAME)
                .claim("custom:role", "ORGANIZER")
                .build();
        signIn(new JwtAuthenticationToken(jwt));

        assertThatCode(() -> AudienceGuard.refuseAgentTokens("mail-center send")).doesNotThrowAnyException();
    }

    @Test
    void should_pass_when_mockUserSignedIn() {
        signIn(new UsernamePasswordAuthenticationToken(USERNAME, "n/a",
                AuthorityUtils.createAuthorityList("ROLE_ORGANIZER")));

        assertThatCode(() -> AudienceGuard.refuseAgentTokens("mail-center send")).doesNotThrowAnyException();
    }

    @Test
    void should_pass_when_noAuthentication() {
        assertThatCode(() -> AudienceGuard.refuseAgentTokens("newsletter send")).doesNotThrowAnyException();
    }
}
