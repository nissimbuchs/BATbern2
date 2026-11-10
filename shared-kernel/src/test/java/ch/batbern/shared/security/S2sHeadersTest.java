package ch.batbern.shared.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class S2sHeadersTest {

    private static final Instant NOW = Instant.parse("2026-11-10T22:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private static void signIn(Instant expiresAt) {
        Jwt.Builder jwt = Jwt.withTokenValue("user-token").header("alg", "none").claim("sub", "organizer");
        if (expiresAt != null) {
            jwt.issuedAt(NOW.minusSeconds(600)).expiresAt(expiresAt);
        }
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt.build()));
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void should_forwardBearerToken_when_jwtValidBeyondMargin() {
        signIn(NOW.plus(S2sHeaders.EXPIRY_MARGIN).plusSeconds(1));

        HttpHeaders headers = S2sHeaders.forCurrentUser(CLOCK);

        assertThat(headers.getFirst(HttpHeaders.AUTHORIZATION)).isEqualTo("Bearer user-token");
        assertThat(headers.size()).isEqualTo(1);
    }

    @Test
    void should_sendNoAuthorization_when_jwtExpiresWithinMargin() {
        signIn(NOW.plus(S2sHeaders.EXPIRY_MARGIN));

        assertThat(S2sHeaders.forCurrentUser(CLOCK).isEmpty()).isTrue();
    }

    @Test
    void should_sendNoAuthorization_when_jwtExpired() {
        signIn(NOW.minusSeconds(1));

        assertThat(S2sHeaders.forCurrentUser(CLOCK).isEmpty()).isTrue();
    }

    @Test
    void should_sendNoAuthorization_when_jwtHasNoExpiry() {
        signIn(null);

        assertThat(S2sHeaders.forCurrentUser(CLOCK).isEmpty()).isTrue();
    }

    @Test
    void should_sendNoAuthorization_when_noAuthentication() {
        HttpHeaders headers = S2sHeaders.forCurrentUser();

        assertThat(headers.isEmpty()).isTrue();
    }

    @Test
    void should_sendNoAuthorization_when_principalIsNotAJwt() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "organizer.alice", null, AuthorityUtils.createAuthorityList("ROLE_ORGANIZER")));

        assertThat(S2sHeaders.forCurrentUser().isEmpty()).isTrue();
    }

    @Test
    void should_sendNoAuthorization_when_anonymous() {
        SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken(
                "key", "anonymousUser", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));

        assertThat(S2sHeaders.forCurrentUser().isEmpty()).isTrue();
    }

    @Test
    void should_returnFreshHeaders_eachCall() {
        HttpHeaders first = S2sHeaders.forCurrentUser();
        first.set(HttpHeaders.CONTENT_TYPE, "application/json");

        assertThat(S2sHeaders.forCurrentUser().isEmpty()).isTrue();
    }
}
