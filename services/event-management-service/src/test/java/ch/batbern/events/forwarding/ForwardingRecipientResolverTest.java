package ch.batbern.events.forwarding;

import ch.batbern.events.client.UserApiClient;
import ch.batbern.events.dto.generated.users.AdditionalEmail;
import ch.batbern.events.dto.generated.users.UserResponse;
import ch.batbern.events.exception.UserNotFoundException;
import ch.batbern.events.service.AdminSettingsService;
import ch.batbern.events.service.DistributionListService;
import ch.batbern.shared.exception.NotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/** Ports T7 of the Lambda's email-forwarder.test.ts. */
@ExtendWith(MockitoExtension.class)
@DisplayName("ForwardingRecipientResolver")
class ForwardingRecipientResolverTest {

    @Mock
    private UserApiClient userApiClient;
    @Mock
    private AdminSettingsService adminSettingsService;
    @Mock
    private DistributionListService distributionListService;

    private ForwardingRecipientResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new ForwardingRecipientResolver(new ForwardingDirectory(userApiClient), adminSettingsService,
                distributionListService);
    }

    private static ForwardingAlias alias(String localPart) {
        return ForwardingAlias.parse(localPart + "@batbern.ch", "batbern.ch").orElseThrow();
    }

    private void organizers() {
        when(userApiClient.getOrganizerUsernames()).thenReturn(List.of("alice", "bob"));
        when(userApiClient.getUserByUsername("alice")).thenReturn(new UserResponse().id("alice")
                .email("Alice@Example.org")
                .addAdditionalEmailsItem(new AdditionalEmail("alice.legacy@example.org", OffsetDateTime.now()))
                .addAdditionalEmailsItem(new AdditionalEmail("ALICE@example.org", OffsetDateTime.now())));
        when(userApiClient.getUserByUsername("bob")).thenReturn(new UserResponse().id("bob").email("bob@example.org"));
    }

    @Test
    @DisplayName("should_resolveOrganizersWithAdditionalEmails_when_okInfoOrEvents")
    void should_resolveOrganizersWithAdditionalEmails_when_okInfoOrEvents() {
        organizers();

        for (String localPart : new String[] {"ok", "info", "events"}) {
            assertThat(resolver.resolve(alias(localPart)))
                    .as(localPart)
                    .containsExactly("alice@example.org", "alice.legacy@example.org", "bob@example.org");
        }
    }

    @Test
    @DisplayName("should_skipUser_when_userNotFoundInCums")
    void should_skipUser_when_userNotFoundInCums() {
        when(userApiClient.getOrganizerUsernames()).thenReturn(List.of("gone", "bob"));
        when(userApiClient.getUserByUsername("gone")).thenThrow(new UserNotFoundException("gone"));
        when(userApiClient.getUserByUsername("bob")).thenReturn(new UserResponse().id("bob").email("bob@example.org"));

        assertThat(resolver.resolve(alias("info"))).containsExactly("bob@example.org");
    }

    @Test
    @DisplayName("should_resolvePartners_when_partnerAddress")
    void should_resolvePartners_when_partnerAddress() {
        when(userApiClient.getPartnerUsernames()).thenReturn(List.of("p1"));
        when(userApiClient.getUserByUsername("p1")).thenReturn(new UserResponse().id("p1").email("p1@example.org"));

        assertThat(resolver.resolve(alias("partner"))).containsExactly("p1@example.org");
    }

    @Test
    @DisplayName("should_resolveSupportContacts_when_configured")
    void should_resolveSupportContacts_when_configured() {
        when(adminSettingsService.getSetting(ForwardingRecipientResolver.SUPPORT_CONTACTS_KEY))
                .thenReturn(Optional.of(" Help@Example.org , second@example.org,, "));

        assertThat(resolver.resolve(alias("support"))).containsExactly("help@example.org", "second@example.org");
    }

    @Test
    @DisplayName("should_fallBackToOrganizers_when_supportContactsEmpty")
    void should_fallBackToOrganizers_when_supportContactsEmpty() {
        when(adminSettingsService.getSetting(ForwardingRecipientResolver.SUPPORT_CONTACTS_KEY))
                .thenReturn(Optional.of("  "));
        organizers();

        assertThat(resolver.resolve(alias("support"))).contains("alice@example.org", "bob@example.org");
    }

    @Test
    @DisplayName("should_useDistributionLists_when_eventAliases")
    void should_useDistributionLists_when_eventAliases() {
        when(distributionListService.resolveSpeakers("BATbern60")).thenReturn(set("s@example.org"));
        when(distributionListService.resolveModerator("BATbern60")).thenReturn(set("m@example.org"));
        when(distributionListService.resolveParticipants("BATbern60")).thenReturn(set("p@example.org"));
        when(distributionListService.resolveParticipants("BATbern58")).thenReturn(set("old@example.org"));

        assertThat(resolver.resolve(alias("batbern60-speaker"))).containsExactly("s@example.org");
        assertThat(resolver.resolve(alias("batbern60-moderator"))).containsExactly("m@example.org");
        assertThat(resolver.resolve(alias("batbern60-participants"))).containsExactly("p@example.org");
        assertThat(resolver.resolve(alias("batbern58"))).containsExactly("old@example.org");
    }

    @Test
    @DisplayName("should_returnEmpty_when_eventUnknown")
    void should_returnEmpty_when_eventUnknown() {
        when(distributionListService.resolveParticipants("BATbern999"))
                .thenThrow(new NotFoundException("Event not found: BATbern999"));

        assertThat(resolver.resolve(alias("batbern999-participants"))).isEmpty();
    }

    @Test
    @DisplayName("should_returnEmpty_when_unknownOrNoReplyAlias")
    void should_returnEmpty_when_unknownOrNoReplyAlias() {
        assertThat(resolver.resolve(alias("whoever"))).isEmpty();
        assertThat(resolver.resolve(alias("noreply"))).isEmpty();
    }

    private static Set<String> set(String... values) {
        return new LinkedHashSet<>(List.of(values));
    }
}
