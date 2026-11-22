package ch.batbern.shared.security;

import ch.batbern.shared.test.security.TestTokens;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The recorded actor (agentic interaction, story 0.2): {@code agent:<run> for <username>} for a token
 * carrying {@code act}, the username otherwise, {@code system:<job>} without a user. Agent tokens are
 * real: minted by {@link TestTokens} and decoded by the shared decoder.
 */
@DisplayName("Actors")
class ActorsTest {

    private static final String SUB = "0d6f4c1e-agent-sub";
    private static final String USERNAME = "hong.an";
    private static final String RUN = "run-7f3a";
    private static final String PRINCIPAL = "organizer";

    private static final BatbernJwtDecoder DECODER = BatbernJwtDecoder.builder()
            .agent((RSAPublicKey) TestTokens.agentKeyPair().getPublic())
            .build();

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private static Jwt agentJwt() {
        return DECODER.decode(TestTokens.agentToken(SUB, USERNAME, RUN, PRINCIPAL));
    }

    private static Jwt cognitoJwt(String username) {
        Jwt.Builder builder = Jwt.withTokenValue("cognito").header("alg", "RS256")
                .subject("cognito-sub").issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(600));
        if (username != null) {
            builder.claim("custom:username", username);
        }
        return builder.build();
    }

    private static void signIn(Authentication authentication) {
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    @Test
    void should_readRunPrincipalAndUsername_when_agentTokenDecoded() {
        Actors.AgentAct act = Actors.agentAct(agentJwt()).orElseThrow();

        assertThat(act.run()).isEqualTo(RUN);
        assertThat(act.principal()).isEqualTo(PRINCIPAL);
        assertThat(act.username()).isEqualTo(USERNAME);
    }

    @Test
    void should_recordAgentForUsername_when_agentTokenSignedIn() {
        signIn(new JwtAuthenticationToken(agentJwt()));

        assertThat(Actors.forUsername(USERNAME)).isEqualTo("agent:run-7f3a for hong.an");
        assertThat(Actors.currentAgentAct()).isPresent();
        assertThat(Actors.carriesAct(SecurityContextHolder.getContext().getAuthentication())).isTrue();
    }

    @Test
    void should_recordAgentForUsername_when_principalIsTheJwtItself() {
        // An authentication whose principal is the Jwt but which is no JwtAuthenticationToken
        signIn(new UsernamePasswordAuthenticationToken(agentJwt(), null, AuthorityUtils.NO_AUTHORITIES));

        assertThat(Actors.forUsername(USERNAME)).isEqualTo("agent:run-7f3a for hong.an");
    }

    @Test
    void should_returnAgentActor_when_forJwtOfAgentToken() {
        assertThat(Actors.forJwt(agentJwt())).isEqualTo("agent:run-7f3a for hong.an");
    }

    @Test
    void should_returnUsername_when_cognitoTokenWithoutAct() {
        Jwt cognito = cognitoJwt("brigitte.bruennimann");
        signIn(new JwtAuthenticationToken(cognito));

        assertThat(Actors.forUsername("brigitte.bruennimann")).isEqualTo("brigitte.bruennimann");
        assertThat(Actors.forJwt(cognito)).isEqualTo("brigitte.bruennimann");
        assertThat(Actors.agentAct(cognito)).isEmpty();
        assertThat(Actors.carriesAct(SecurityContextHolder.getContext().getAuthentication())).isFalse();
        assertThat(Actors.currentAgentAct()).isEmpty();
    }

    @Test
    void should_fallBackToSub_when_forJwtWithoutUsername() {
        assertThat(Actors.forJwt(cognitoJwt(null))).isEqualTo("cognito-sub");
        assertThat(Actors.forJwt(cognitoJwt("  "))).isEqualTo("cognito-sub");
    }

    @Test
    void should_returnUsername_when_mockUserSignedIn() {
        signIn(new UsernamePasswordAuthenticationToken("organizer", "pw",
                AuthorityUtils.createAuthorityList("ROLE_ORGANIZER")));

        assertThat(Actors.forUsername("organizer")).isEqualTo("organizer");
        assertThat(Actors.carriesAct(SecurityContextHolder.getContext().getAuthentication())).isFalse();
    }

    @Test
    void should_returnValue_when_noAuthenticationOrAnonymous() {
        assertThat(Actors.forUsername("organizer")).isEqualTo("organizer");
        assertThat(Actors.carriesAct(null)).isFalse();

        signIn(new AnonymousAuthenticationToken("key", "anonymousUser",
                AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));
        assertThat(Actors.forUsername("organizer")).isEqualTo("organizer");
        assertThat(Actors.currentAgentAct()).isEmpty();
    }

    @Test
    void should_keepNull_when_valueIsNull() {
        signIn(new JwtAuthenticationToken(agentJwt()));

        assertThat(Actors.forUsername(null)).isNull();
    }

    @Test
    void should_notWrapTwice_when_valueAlreadyResolved() {
        signIn(new JwtAuthenticationToken(agentJwt()));
        String once = Actors.forUsername(USERNAME);

        assertThat(Actors.forUsername(once)).isEqualTo(once);
        assertThat(Actors.forUsername("system:agenda-publishing")).isEqualTo("system:agenda-publishing");
    }

    @Test
    void should_nameSystemJob_when_noUser() {
        assertThat(Actors.system("admin-settings")).isEqualTo("system:admin-settings");
        assertThat(Actors.system("s3-photo-2")).isEqualTo("system:s3-photo-2");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "Admin", "admin settings", "admin_settings", "system:x", "ä"})
    void should_rejectJob_when_blankOrNotKebabCase(String job) {
        assertThatThrownBy(() -> Actors.system(job)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void should_rejectJob_when_null() {
        assertThatThrownBy(() -> Actors.system(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void should_carryActButNoAgentAct_when_actHasNoRun() {
        // The decoder refuses such a token; this guards the resolver on its own.
        Jwt jwt = Jwt.withTokenValue("t").header("alg", "RS256").subject(SUB)
                .claim("custom:username", USERNAME)
                .claim("act", Map.of("principal", PRINCIPAL))
                .build();
        signIn(new JwtAuthenticationToken(jwt));

        assertThat(Actors.carriesAct(SecurityContextHolder.getContext().getAuthentication())).isTrue();
        assertThat(Actors.agentAct(jwt)).isEmpty();
        assertThat(Actors.forUsername(USERNAME)).isEqualTo(USERNAME);
        assertThat(Actors.forJwt(jwt)).isEqualTo(USERNAME);
    }

    @Test
    void should_fitEveryWidenedColumn_when_longestRunAndUsername() {
        String run = "0".repeat(36);
        String username = "u".repeat(100);
        Jwt jwt = DECODER.decode(TestTokens.agentToken(SUB, username, run, PRINCIPAL));

        String actor = Actors.forJwt(jwt);

        assertThat(actor).hasSize(147).startsWith(Actors.AGENT_PREFIX).endsWith(" for " + username);
        assertThat(actor.length()).isLessThanOrEqualTo(255);
    }

    @Test
    void should_readPersonBehindActor_when_usernameOfActor() {
        assertThat(Actors.usernameOf("agent:run-7f3a for hong.an")).isEqualTo("hong.an");
        assertThat(Actors.usernameOf("hong.an")).isEqualTo("hong.an");
        assertThat(Actors.usernameOf("system:agenda-publishing")).isNull();
        assertThat(Actors.usernameOf(null)).isNull();
    }
}
