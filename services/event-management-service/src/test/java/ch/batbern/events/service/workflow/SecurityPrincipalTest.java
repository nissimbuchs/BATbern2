package ch.batbern.events.service.workflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/**
 * Unit tests for {@link SecurityPrincipal}, focused on Story 11.E.3's
 * {@link SecurityPrincipal#fromAuthentication(Authentication)} factory.
 */
class SecurityPrincipalTest {

    @Test
    @DisplayName("should_extractUsernameAndRoles_when_authenticationIsPopulated")
    void fromAuthentication_extractsUsernameAndRoles() {
        Authentication auth =
                new UsernamePasswordAuthenticationToken(
                        "alice.example",
                        "n/a",
                        List.of(
                                new SimpleGrantedAuthority("ROLE_SPEAKER"),
                                new SimpleGrantedAuthority("ROLE_ORGANIZER")));

        SecurityPrincipal principal = SecurityPrincipal.fromAuthentication(auth);

        assertThat(principal.username()).isEqualTo("alice.example");
        assertThat(principal.roles()).containsExactly("SPEAKER", "ORGANIZER");
        assertThat(principal.hasRole("SPEAKER")).isTrue();
        assertThat(principal.hasRole("PARTNER")).isFalse();
    }

    @Test
    @DisplayName("should_preservePlainAuthorities_when_authoritiesLackRolePrefix")
    void fromAuthentication_keepsPlainAuthorities() {
        // Some auth converters (e.g. raw Cognito custom-claim mapping) hand back authorities
        // without the Spring "ROLE_" prefix. The factory must not double-strip or mangle them.
        Authentication auth =
                new UsernamePasswordAuthenticationToken(
                        "bob.example",
                        "n/a",
                        List.of(new SimpleGrantedAuthority("SPEAKER")));

        SecurityPrincipal principal = SecurityPrincipal.fromAuthentication(auth);

        assertThat(principal.roles()).containsExactly("SPEAKER");
    }

    @Test
    @DisplayName("should_returnEmptyRoles_when_authoritiesIsEmpty")
    void fromAuthentication_handlesEmptyAuthorities() {
        Authentication auth = new UsernamePasswordAuthenticationToken("anon", "n/a", List.of());

        SecurityPrincipal principal = SecurityPrincipal.fromAuthentication(auth);

        assertThat(principal.roles()).isEmpty();
    }

    @Test
    @DisplayName("should_throwIllegalArgument_when_authenticationIsNull")
    void fromAuthentication_rejectsNullAuthentication() {
        assertThatThrownBy(() -> SecurityPrincipal.fromAuthentication(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("non-null Authentication");
    }
}
