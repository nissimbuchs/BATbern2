package ch.batbern.events.service;

import ch.batbern.events.client.UserApiClient;
import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.Registration;
import ch.batbern.events.domain.SessionUser;
import ch.batbern.events.dto.generated.users.AdditionalEmail;
import ch.batbern.events.dto.generated.users.UserResponse;
import ch.batbern.events.exception.UserNotFoundException;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.repository.RegistrationRepository;
import ch.batbern.events.repository.SessionUserRepository;
import ch.batbern.shared.exception.NotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link DistributionListService}.
 *
 * <p>Spec: {@code _bmad-output/implementation-artifacts/spec-auto-participant-email-aliases-excel-export.md}
 * (F2).
 */
@ExtendWith(MockitoExtension.class)
class DistributionListServiceTest {

    @Mock
    private EventRepository eventRepository;

    @Mock
    private SessionUserRepository sessionUserRepository;

    @Mock
    private RegistrationRepository registrationRepository;

    @Mock
    private UserApiClient userApiClient;

    @InjectMocks
    private DistributionListService service;

    private static final UUID EVENT_ID = UUID.randomUUID();
    private static final String EVENT_CODE = "BATbern57";

    private Event event;

    @BeforeEach
    void setUp() {
        event = Event.builder()
                .id(EVENT_ID)
                .eventCode(EVENT_CODE)
                .organizerUsername("organizer.alice")
                .build();
    }

    @Test
    @DisplayName("speakers: fans out PRIMARY_SPEAKER + additionalEmails, lowercased & deduped")
    void should_returnPrimarySpeakerEmails_withAdditionalEmails() {
        when(eventRepository.findByEventCode(EVENT_CODE)).thenReturn(Optional.of(event));
        SessionUser su1 = sessionUserOf("speaker.bob");
        SessionUser su2 = sessionUserOf("speaker.carol");
        when(sessionUserRepository.findScheduledPrimarySpeakersByEventId(EVENT_ID))
                .thenReturn(List.of(su1, su2));
        when(userApiClient.getUserByUsername("speaker.bob")).thenReturn(
                userWithAdditional("speaker.bob", "Bob@Example.com",
                        List.of("bob.work@example.com")));
        when(userApiClient.getUserByUsername("speaker.carol")).thenReturn(
                userWithAdditional("speaker.carol", "carol@example.com", List.of()));

        Set<String> emails = service.resolveSpeakers(EVENT_CODE);

        assertThat(emails).containsExactly(
                "bob@example.com",
                "bob.work@example.com",
                "carol@example.com"
        );
    }

    @Test
    @DisplayName("speakers: deduplicates case-insensitively across users (overlap on shared email)")
    void should_dedupCaseInsensitive_acrossSpeakers() {
        when(eventRepository.findByEventCode(EVENT_CODE)).thenReturn(Optional.of(event));
        SessionUser su1 = sessionUserOf("speaker.bob");
        SessionUser su2 = sessionUserOf("speaker.bob.alt"); // same person, different login
        when(sessionUserRepository.findScheduledPrimarySpeakersByEventId(EVENT_ID))
                .thenReturn(List.of(su1, su2));
        when(userApiClient.getUserByUsername("speaker.bob")).thenReturn(
                userWithAdditional("speaker.bob", "BOB@example.com", List.of()));
        when(userApiClient.getUserByUsername("speaker.bob.alt")).thenReturn(
                userWithAdditional("speaker.bob.alt", "bob@EXAMPLE.com", List.of()));

        Set<String> emails = service.resolveSpeakers(EVENT_CODE);

        assertThat(emails).containsExactly("bob@example.com");
    }

    @Test
    @DisplayName("speakers: UserNotFoundException is skipped silently (no exception thrown)")
    void should_skipMissingUser_when_userApiClientThrows() {
        when(eventRepository.findByEventCode(EVENT_CODE)).thenReturn(Optional.of(event));
        SessionUser su1 = sessionUserOf("speaker.bob");
        SessionUser su2 = sessionUserOf("speaker.missing");
        when(sessionUserRepository.findScheduledPrimarySpeakersByEventId(EVENT_ID))
                .thenReturn(List.of(su1, su2));
        when(userApiClient.getUserByUsername("speaker.bob")).thenReturn(
                userWithAdditional("speaker.bob", "bob@example.com", List.of()));
        when(userApiClient.getUserByUsername("speaker.missing"))
                .thenThrow(new UserNotFoundException("speaker.missing"));

        Set<String> emails = service.resolveSpeakers(EVENT_CODE);

        assertThat(emails).containsExactly("bob@example.com");
    }

    @Test
    @DisplayName("speakers: empty list when event has no scheduled primary speakers")
    void should_returnEmpty_when_noScheduledPrimarySpeakers() {
        when(eventRepository.findByEventCode(EVENT_CODE)).thenReturn(Optional.of(event));
        when(sessionUserRepository.findScheduledPrimarySpeakersByEventId(EVENT_ID))
                .thenReturn(List.of());

        Set<String> emails = service.resolveSpeakers(EVENT_CODE);

        assertThat(emails).isEmpty();
    }

    @Test
    @DisplayName("speakers: unknown event throws NotFoundException")
    void should_throwNotFound_when_eventCodeUnknown() {
        when(eventRepository.findByEventCode("BATbern999")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.resolveSpeakers("BATbern999"))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("moderator: returns organizer email + additionalEmails, lowercased")
    void should_returnOrganizerEmails_when_moderatorKindRequested() {
        when(eventRepository.findByEventCode(EVENT_CODE)).thenReturn(Optional.of(event));
        when(userApiClient.getUserByUsername("organizer.alice")).thenReturn(
                userWithAdditional("organizer.alice", "Alice@batbern.ch",
                        List.of("alice.private@example.com")));

        Set<String> emails = service.resolveModerator(EVENT_CODE);

        assertThat(emails).containsExactly("alice@batbern.ch", "alice.private@example.com");
    }

    @Test
    @DisplayName("moderator: organizer missing in CUMS yields empty list (no throw)")
    void should_returnEmpty_when_organizerMissing() {
        when(eventRepository.findByEventCode(EVENT_CODE)).thenReturn(Optional.of(event));
        when(userApiClient.getUserByUsername("organizer.alice"))
                .thenThrow(new UserNotFoundException("organizer.alice"));

        Set<String> emails = service.resolveModerator(EVENT_CODE);

        assertThat(emails).isEmpty();
    }

    @Test
    @DisplayName("moderator: unknown event throws NotFoundException")
    void should_throwNotFound_when_eventCodeUnknownOnModerator() {
        when(eventRepository.findByEventCode("BATbern999")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.resolveModerator("BATbern999"))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("participants: returns active registrants' attendeeEmail, lowercased & deduped")
    void should_returnActiveRegistrantEmails_when_participantsKindRequested() {
        when(eventRepository.findByEventCode(EVENT_CODE)).thenReturn(Optional.of(event));
        when(registrationRepository.findByEventIdAndStatusIn(EVENT_ID, Registration.CONFIRMED_STATUSES))
                .thenReturn(List.of(
                        registrationOf("Anna@Example.com", "anna.attendee"),
                        registrationOf("ben@example.com", "ben.attendee"),
                        registrationOf("anna@EXAMPLE.com", "anna.dup"))); // same person, deduped

        Set<String> emails = service.resolveParticipants(EVENT_CODE);

        assertThat(emails).containsExactly("anna@example.com", "ben@example.com");
    }

    @Test
    @DisplayName("participants: falls back to CUMS lookup when attendeeEmail is blank")
    void should_lookupUserEmail_when_attendeeEmailBlank() {
        when(eventRepository.findByEventCode(EVENT_CODE)).thenReturn(Optional.of(event));
        when(registrationRepository.findByEventIdAndStatusIn(EVENT_ID, Registration.CONFIRMED_STATUSES))
                .thenReturn(List.of(registrationOf(null, "user.noemail")));
        when(userApiClient.getUserByUsername("user.noemail")).thenReturn(
                userWithAdditional("user.noemail", "resolved@example.com", List.of()));

        Set<String> emails = service.resolveParticipants(EVENT_CODE);

        assertThat(emails).containsExactly("resolved@example.com");
    }

    @Test
    @DisplayName("participants: empty list when event has no active registrants")
    void should_returnEmpty_when_noActiveRegistrants() {
        when(eventRepository.findByEventCode(EVENT_CODE)).thenReturn(Optional.of(event));
        when(registrationRepository.findByEventIdAndStatusIn(EVENT_ID, Registration.CONFIRMED_STATUSES))
                .thenReturn(List.of());

        Set<String> emails = service.resolveParticipants(EVENT_CODE);

        assertThat(emails).isEmpty();
    }

    @Test
    @DisplayName("participants: unknown event throws NotFoundException")
    void should_throwNotFound_when_eventCodeUnknownOnParticipants() {
        when(eventRepository.findByEventCode("BATbern999")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.resolveParticipants("BATbern999"))
                .isInstanceOf(NotFoundException.class);
    }

    // ---- helpers ----

    private Registration registrationOf(String attendeeEmail, String attendeeUsername) {
        return Registration.builder()
                .attendeeEmail(attendeeEmail)
                .attendeeUsername(attendeeUsername)
                .build();
    }

    private SessionUser sessionUserOf(String username) {
        SessionUser su = new SessionUser();
        su.setUsername(username);
        su.setSpeakerRole(SessionUser.SpeakerRole.PRIMARY_SPEAKER);
        // Session FK is irrelevant for the service test (resolver only uses the username)
        return su;
    }

    private UserResponse userWithAdditional(String username, String email, List<String> extras) {
        UserResponse user = new UserResponse()
                .id(username)
                .email(email);
        for (String e : extras) {
            user.addAdditionalEmailsItem(new AdditionalEmail(e, OffsetDateTime.now()));
        }
        return user;
    }
}
