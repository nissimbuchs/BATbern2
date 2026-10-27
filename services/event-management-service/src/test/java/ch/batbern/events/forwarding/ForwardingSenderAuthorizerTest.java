package ch.batbern.events.forwarding;

import ch.batbern.events.exception.UserServiceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Ports T8 of the Lambda's email-forwarder.test.ts, plus the From authentication it lacked. */
@ExtendWith(MockitoExtension.class)
@DisplayName("ForwardingSenderAuthorizer")
class ForwardingSenderAuthorizerTest {

    private static final MailAuthentication PASS =
            MailAuthentication.fromHeaders(new String[] {"amazonses.com; dkim=pass header.i=@example.org; dmarc=pass;"});
    private static final MailAuthentication FAIL =
            MailAuthentication.fromHeaders(new String[] {"amazonses.com; spf=pass; dkim=none; dmarc=fail;"});

    @Mock
    private ForwardingDirectory directory;

    private ForwardingSenderAuthorizer authorizer;

    @BeforeEach
    void setUp() {
        authorizer = new ForwardingSenderAuthorizer(directory);
    }

    private static ForwardingAlias alias(String localPart) {
        return ForwardingAlias.parse(localPart + "@batbern.ch", "batbern.ch").orElseThrow();
    }

    @Test
    @DisplayName("should_allowAnyone_when_contactAlias")
    void should_allowAnyone_when_contactAlias() {
        for (String localPart : new String[] {"info", "events", "support", "batbern60-moderator"}) {
            assertThat(authorizer.isAuthorized(alias(localPart), "stranger@example.net", FAIL)).as(localPart).isTrue();
        }
        verifyNoInteractions(directory);
    }

    @Test
    @DisplayName("should_allowAuthenticatedOrganizer_when_organizerOnlyAlias")
    void should_allowAuthenticatedOrganizer_when_organizerOnlyAlias() {
        when(directory.organizerEmails()).thenReturn(Set.of("alice@example.org", "alice.legacy@example.org"));

        for (String localPart : new String[] {"ok", "partner", "batbern58", "batbern60-speaker",
            "batbern60-participants"}) {
            assertThat(authorizer.isAuthorized(alias(localPart), "Alice@Example.org", PASS)).as(localPart).isTrue();
        }
    }

    @Test
    @DisplayName("should_allowOrganizer_when_sendingFromAdditionalEmail")
    void should_allowOrganizer_when_sendingFromAdditionalEmail() {
        when(directory.organizerEmails()).thenReturn(Set.of("alice@example.org", "alice.legacy@example.org"));

        assertThat(authorizer.isAuthorized(alias("ok"), "alice.legacy@example.org", PASS)).isTrue();
    }

    @Test
    @DisplayName("should_reject_when_nonOrganizerUsesOrganizerOnlyAlias")
    void should_reject_when_nonOrganizerUsesOrganizerOnlyAlias() {
        when(directory.organizerEmails()).thenReturn(Set.of("alice@example.org"));

        assertThat(authorizer.isAuthorized(alias("batbern60-participants"), "mallory@example.org", PASS)).isFalse();
    }

    @Test
    @DisplayName("should_reject_when_organizerFromIsNotAuthenticated")
    void should_reject_when_organizerFromIsNotAuthenticated() {
        when(directory.organizerEmails()).thenReturn(Set.of("alice@example.org"));

        assertThat(authorizer.isAuthorized(alias("batbern60-participants"), "alice@example.org", FAIL)).isFalse();
    }

    @Test
    @DisplayName("should_propagate_when_organizerListUnavailable_soTheMailIsRetriedNotRejected")
    void should_propagate_when_organizerListUnavailable_soTheMailIsRetriedNotRejected() {
        when(directory.organizerEmails()).thenThrow(new UserServiceException("CUMS down"));

        assertThatThrownBy(() -> authorizer.isAuthorized(alias("ok"), "alice@example.org", PASS))
                .isInstanceOf(UserServiceException.class);
    }
}
