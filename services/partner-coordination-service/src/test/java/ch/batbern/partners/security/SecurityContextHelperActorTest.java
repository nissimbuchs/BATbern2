package ch.batbern.partners.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The actor methods of {@link SecurityContextHelper} (agentic interaction 0.2): the recorded actor is
 * {@code agent:<run> for <username>} under an agent token, the username otherwise, and
 * {@code system:<job>} without a user; the username itself stays the username.
 */
class SecurityContextHelperActorTest {

    private final SecurityContextHelper helper = new SecurityContextHelper();

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private static void signIn(Jwt jwt) {
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt, List.of()));
    }

    private static Jwt jwt(boolean agent) {
        Jwt.Builder builder = Jwt.withTokenValue("t").header("alg", "RS256").subject("some-sub")
                .claim("custom:username", "hong.an");
        if (agent) {
            builder.claim("act", Map.of("run", "run-1", "principal", "organizer-assistant"));
        }
        return builder.build();
    }

    @Test
    void should_recordAgentActor_when_agentTokenSignedIn() {
        signIn(jwt(true));

        assertThat(helper.getCurrentUsername()).isEqualTo("hong.an");
        assertThat(helper.getCurrentActor()).isEqualTo("agent:run-1 for hong.an");
        assertThat(helper.actorOr("some-job")).isEqualTo("agent:run-1 for hong.an");
        assertThat(helper.carriesAgentAct()).isTrue();
    }

    @Test
    void should_recordUsername_when_cognitoTokenSignedIn() {
        signIn(jwt(false));

        assertThat(helper.getCurrentActor()).isEqualTo("hong.an");
        assertThat(helper.actorOr("some-job")).isEqualTo("hong.an");
        assertThat(helper.carriesAgentAct()).isFalse();
    }

    @Test
    void should_recordUsername_when_mockUserSignedIn() {
        User user = new User("organizer", "pw", AuthorityUtils.createAuthorityList("ROLE_ORGANIZER"));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));

        assertThat(helper.actorOr("some-job")).isEqualTo("organizer");
        assertThat(helper.carriesAgentAct()).isFalse();
    }

    @Test
    void should_recordSystemJob_when_noUserOrAnonymous() {
        assertThat(helper.actorOr("some-job")).isEqualTo("system:some-job");

        SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken("key",
                "anonymousUser", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));
        assertThat(helper.actorOr("some-job")).isEqualTo("system:some-job");
        assertThat(helper.carriesAgentAct()).isFalse();
    }
}
