package ch.batbern.events.service;

import ch.batbern.events.client.UserApiClient;
import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.Registration;
import ch.batbern.events.domain.SessionUser;
import ch.batbern.events.dto.generated.users.UserResponse;
import ch.batbern.events.exception.UserNotFoundException;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.repository.RegistrationRepository;
import ch.batbern.events.repository.SessionUserRepository;
import ch.batbern.shared.types.Role;
import ch.batbern.shared.exception.NotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ParticipantsCollector}: dedupe + role precedence carry
 * over from the previous {@code ParticipantsExportService} tests; these tests
 * additionally lock in the canonical sort applied at the end (role precedence
 * → last name via German collator → first name).
 *
 * <p>Spec: {@code _bmad-output/implementation-artifacts/spec-auto-participant-email-aliases-excel-export.md}
 * — DOCX-export extension.
 */
@ExtendWith(MockitoExtension.class)
class ParticipantsCollectorTest {

    @Mock
    private EventRepository eventRepository;
    @Mock
    private RegistrationRepository registrationRepository;
    @Mock
    private SessionUserRepository sessionUserRepository;
    @Mock
    private UserApiClient userApiClient;

    private ParticipantsCollector collector;

    private static final UUID EVENT_ID = UUID.randomUUID();
    private static final String EVENT_CODE = "BATbern57";
    private Event event;

    @BeforeEach
    void setUp() {
        collector = new ParticipantsCollector(
                eventRepository, registrationRepository, sessionUserRepository, userApiClient);
        event = Event.builder().id(EVENT_ID).eventCode(EVENT_CODE).build();
        // The event-found stub is added per-test (rather than here) so the
        // unknown-event test can stub a different event code without tripping
        // Mockito's strict unnecessary-stubbing check.
    }

    private void stubEventFound() {
        when(eventRepository.findByEventCode(EVENT_CODE)).thenReturn(Optional.of(event));
    }

    @Test
    @DisplayName("Canonical order: role precedence Organisator → Referent → Teilnehmer")
    void should_sortByRolePrecedence() {
        stubEventFound();
        // Set up: 1 organizer, 1 speaker, 1 attendee — input order is reversed
        // to confirm the sort actually does work.
        when(userApiClient.getOrganizerUsernames()).thenReturn(List.of("o.one"));
        when(userApiClient.getUserByUsername("o.one"))
                .thenReturn(user("o.one", "Olivia", "Org", null));

        SessionUser speaker = new SessionUser();
        speaker.setUsername("s.one");
        speaker.setSpeakerRole(SessionUser.SpeakerRole.PRIMARY_SPEAKER);
        when(sessionUserRepository.findEventSpeakersByEventId(EVENT_ID))
                .thenReturn(List.of(speaker));
        when(userApiClient.getUserByUsername("s.one"))
                .thenReturn(user("s.one", "Sam", "Spk", null));

        Registration r = Registration.builder()
                .attendeeUsername("a.one")
                .attendeeFirstName("Alex").attendeeLastName("Att")
                .status("confirmed").build();
        when(registrationRepository.findByEventId(EVENT_ID)).thenReturn(List.of(r));

        List<ParticipantRow> rows = collector.collect(EVENT_CODE);

        assertThat(rows).extracting(ParticipantRow::role).containsExactly(
                Role.ORGANIZER,
                Role.SPEAKER,
                Role.ATTENDEE);
    }

    @Test
    @DisplayName("Within a role, last names sort alphabetically via German collator (umlauts)")
    void should_sortByLastName_withGermanCollator() {
        stubEventFound();
        when(userApiClient.getOrganizerUsernames()).thenReturn(List.of());
        when(sessionUserRepository.findEventSpeakersByEventId(EVENT_ID)).thenReturn(List.of());

        // Three attendees: Müller, Niederer, Aebi → German collator gives Aebi, Müller, Niederer.
        List<Registration> regs = List.of(
                Registration.builder().attendeeUsername("u3").attendeeFirstName("Nina")
                        .attendeeLastName("Niederer").status("confirmed").build(),
                Registration.builder().attendeeUsername("u1").attendeeFirstName("Mia")
                        .attendeeLastName("Müller").status("confirmed").build(),
                Registration.builder().attendeeUsername("u2").attendeeFirstName("Anna")
                        .attendeeLastName("Aebi").status("confirmed").build());
        when(registrationRepository.findByEventId(EVENT_ID)).thenReturn(regs);

        List<ParticipantRow> rows = collector.collect(EVENT_CODE);

        assertThat(rows).extracting(ParticipantRow::lastName)
                .containsExactly("Aebi", "Müller", "Niederer");
    }

    @Test
    @DisplayName("Tie-break on equal last names falls through to first name")
    void should_tieBreakByFirstName_whenLastNamesEqual() {
        stubEventFound();
        when(userApiClient.getOrganizerUsernames()).thenReturn(List.of());
        when(sessionUserRepository.findEventSpeakersByEventId(EVENT_ID)).thenReturn(List.of());

        List<Registration> regs = List.of(
                Registration.builder().attendeeUsername("u1").attendeeFirstName("Zoe")
                        .attendeeLastName("Meier").status("confirmed").build(),
                Registration.builder().attendeeUsername("u2").attendeeFirstName("Anna")
                        .attendeeLastName("Meier").status("confirmed").build());
        when(registrationRepository.findByEventId(EVENT_ID)).thenReturn(regs);

        List<ParticipantRow> rows = collector.collect(EVENT_CODE);

        assertThat(rows).extracting(ParticipantRow::firstName).containsExactly("Anna", "Zoe");
    }

    @Test
    @DisplayName("Dedupe across role-source-sets: organizer-also-attendee renders once as Organisator")
    void should_dedupe_organizerAndAttendeeSameUser() {
        stubEventFound();
        when(userApiClient.getOrganizerUsernames()).thenReturn(List.of("dual.user"));
        when(userApiClient.getUserByUsername("dual.user"))
                .thenReturn(user("dual.user", "Dual", "User", null));
        when(sessionUserRepository.findEventSpeakersByEventId(EVENT_ID)).thenReturn(List.of());

        Registration r = Registration.builder()
                .attendeeUsername("dual.user").attendeeFirstName("Dual").attendeeLastName("User")
                .status("confirmed").build();
        when(registrationRepository.findByEventId(EVENT_ID)).thenReturn(List.of(r));

        List<ParticipantRow> rows = collector.collect(EVENT_CODE);

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).role()).isEqualTo(Role.ORGANIZER);
    }

    @Test
    @DisplayName("Attendee enriches via CUMS when registrations.attendee_first/last_name is NULL")
    void should_enrichAttendeesFromCums_when_denormalizedNamesAreNull() {
        stubEventFound();
        when(userApiClient.getCompanyDisplayName("acme")).thenReturn("Acme AG");
        when(userApiClient.getOrganizerUsernames()).thenReturn(List.of());
        when(sessionUserRepository.findEventSpeakersByEventId(EVENT_ID)).thenReturn(List.of());

        // Historical registration with NULL denormalized names (the BAT bug pattern).
        Registration legacy = Registration.builder()
                .attendeeUsername("legacy.attendee")
                .attendeeFirstName(null)
                .attendeeLastName(null)
                .status("attended")
                .build();
        when(registrationRepository.findByEventId(EVENT_ID)).thenReturn(List.of(legacy));
        when(userApiClient.getUserByUsername("legacy.attendee"))
                .thenReturn(user("legacy.attendee", "Legacy", "Attendee", "acme"));

        List<ParticipantRow> rows = collector.collect(EVENT_CODE);

        assertThat(rows).hasSize(1);
        ParticipantRow p = rows.get(0);
        assertThat(p.firstName()).isEqualTo("Legacy");
        assertThat(p.lastName()).isEqualTo("Attendee");
        assertThat(p.companyDisplayName()).isEqualTo("Acme AG");
        assertThat(p.role()).isEqualTo(Role.ATTENDEE);
    }

    @Test
    @DisplayName("Attendee not found in CUMS falls back to registrations denormalized cache fields")
    void should_fallBackToRegistrationCache_when_attendeeMissingInCums() {
        stubEventFound();
        when(userApiClient.getOrganizerUsernames()).thenReturn(List.of());
        when(sessionUserRepository.findEventSpeakersByEventId(EVENT_ID)).thenReturn(List.of());

        // CUMS doesn't know this user, but the registration row has cached names.
        Registration cached = Registration.builder()
                .attendeeUsername("orphan.user")
                .attendeeFirstName("Orphan")
                .attendeeLastName("User")
                .status("confirmed")
                .build();
        when(registrationRepository.findByEventId(EVENT_ID)).thenReturn(List.of(cached));
        when(userApiClient.getUserByUsername("orphan.user"))
                .thenThrow(new UserNotFoundException("orphan.user"));

        List<ParticipantRow> rows = collector.collect(EVENT_CODE);

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).firstName()).isEqualTo("Orphan");
        assertThat(rows.get(0).lastName()).isEqualTo("User");
    }

    @Test
    @DisplayName("Speaker not found in CUMS falls back to session_users cached names")
    void should_fallBackToSessionUserCachedNames_when_userMissing() {
        stubEventFound();
        when(userApiClient.getOrganizerUsernames()).thenReturn(List.of());

        SessionUser speaker = new SessionUser();
        speaker.setUsername("missing.user");
        speaker.setSpeakerFirstName("Cached");
        speaker.setSpeakerLastName("Speaker");
        speaker.setSpeakerRole(SessionUser.SpeakerRole.PRIMARY_SPEAKER);
        when(sessionUserRepository.findEventSpeakersByEventId(EVENT_ID))
                .thenReturn(List.of(speaker));
        when(userApiClient.getUserByUsername("missing.user"))
                .thenThrow(new UserNotFoundException("missing.user"));

        when(registrationRepository.findByEventId(EVENT_ID)).thenReturn(List.of());

        List<ParticipantRow> rows = collector.collect(EVENT_CODE);

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).firstName()).isEqualTo("Cached");
        assertThat(rows.get(0).lastName()).isEqualTo("Speaker");
        assertThat(rows.get(0).role()).isEqualTo(Role.SPEAKER);
    }

    @Test
    @DisplayName("Unknown event → NotFoundException")
    void should_throwNotFound_when_unknownEvent() {
        when(eventRepository.findByEventCode("BATbern999")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> collector.collect("BATbern999"))
                .isInstanceOf(NotFoundException.class);
    }

    // ── #1236: partners on badges and in the participants export ─────────────────────────

    private SessionUser speakerRow(String username) {
        SessionUser su = new SessionUser();
        su.setUsername(username);
        su.setSpeakerRole(SessionUser.SpeakerRole.PRIMARY_SPEAKER);
        return su;
    }

    private Registration registered(String username, String first, String last) {
        return Registration.builder().attendeeUsername(username)
                .attendeeFirstName(first).attendeeLastName(last).status("registered").build();
    }

    @Test
    @DisplayName("#1236: a registered partner gets PARTNER and sorts after speakers, before attendees")
    void should_labelPartnerAndSortAfterSpeakers_when_partnerRegistered() {
        stubEventFound();
        when(userApiClient.getOrganizerUsernames()).thenReturn(List.of());
        when(sessionUserRepository.findEventSpeakersByEventId(EVENT_ID)).thenReturn(List.of(speakerRow("s.one")));
        when(userApiClient.getUserByUsername("s.one")).thenReturn(user("s.one", "Sam", "Zebra", null));
        when(userApiClient.getPartnerUsernames()).thenReturn(List.of("p.one"));
        when(registrationRepository.findByEventId(EVENT_ID)).thenReturn(List.of(
                registered("a.one", "Anna", "Aebi"), registered("p.one", "Paul", "Partner")));
        when(userApiClient.getUserByUsername("a.one")).thenThrow(new UserNotFoundException("a.one"));
        when(userApiClient.getUserByUsername("p.one")).thenReturn(user("p.one", "Paul", "Partner", null));

        List<ParticipantRow> rows = collector.collect(EVENT_CODE);

        assertThat(rows).extracting(ParticipantRow::lastName).containsExactly("Zebra", "Partner", "Aebi");
        assertThat(rows).extracting(ParticipantRow::role)
                .containsExactly(Role.SPEAKER, Role.PARTNER, Role.ATTENDEE);
    }

    @Test
    @DisplayName("#1236: a partner who also speaks gets one badge, as SPEAKER")
    void should_keepSpeaker_when_partnerAlsoSpeaks() {
        stubEventFound();
        when(userApiClient.getOrganizerUsernames()).thenReturn(List.of());
        when(sessionUserRepository.findEventSpeakersByEventId(EVENT_ID)).thenReturn(List.of(speakerRow("p.one")));
        when(userApiClient.getUserByUsername("p.one")).thenReturn(user("p.one", "Paul", "Partner", null));
        when(userApiClient.getPartnerUsernames()).thenReturn(List.of("p.one"));
        when(registrationRepository.findByEventId(EVENT_ID)).thenReturn(List.of(registered("p.one", "Paul", "Partner")));

        List<ParticipantRow> rows = collector.collect(EVENT_CODE);

        assertThat(rows).extracting(ParticipantRow::role).containsExactly(Role.SPEAKER);
    }

    @Test
    @DisplayName("#1236: an organizer who is also a partner gets one badge, as ORGANIZER")
    void should_keepOrganizer_when_organizerAlsoPartner() {
        stubEventFound();
        when(userApiClient.getOrganizerUsernames()).thenReturn(List.of("o.one"));
        when(userApiClient.getUserByUsername("o.one")).thenReturn(user("o.one", "Olga", "Org", null));
        when(sessionUserRepository.findEventSpeakersByEventId(EVENT_ID)).thenReturn(List.of());
        when(userApiClient.getPartnerUsernames()).thenReturn(List.of("o.one"));
        when(registrationRepository.findByEventId(EVENT_ID)).thenReturn(List.of(registered("o.one", "Olga", "Org")));

        List<ParticipantRow> rows = collector.collect(EVENT_CODE);

        assertThat(rows).extracting(ParticipantRow::role).containsExactly(Role.ORGANIZER);
    }

    @Test
    @DisplayName("#1236: being a partner alone does not put someone on the list")
    void should_skipPartner_when_notRegistered() {
        stubEventFound();
        when(userApiClient.getOrganizerUsernames()).thenReturn(List.of());
        when(sessionUserRepository.findEventSpeakersByEventId(EVENT_ID)).thenReturn(List.of());
        when(userApiClient.getPartnerUsernames()).thenReturn(List.of("p.absent"));
        when(registrationRepository.findByEventId(EVENT_ID)).thenReturn(List.of(
                Registration.builder().attendeeUsername("p.absent").attendeeFirstName("Pia")
                        .attendeeLastName("Away").status("cancelled").build()));

        assertThat(collector.collect(EVENT_CODE)).isEmpty();
    }

    @Test
    @DisplayName("#1236: when CUMS cannot list partners, a registered partner falls back to ATTENDEE")
    void should_fallBackToAttendee_when_partnerListUnavailable() {
        stubEventFound();
        when(userApiClient.getOrganizerUsernames()).thenReturn(List.of());
        when(sessionUserRepository.findEventSpeakersByEventId(EVENT_ID)).thenReturn(List.of());
        when(userApiClient.getPartnerUsernames()).thenThrow(new RuntimeException("CUMS down"));
        when(registrationRepository.findByEventId(EVENT_ID)).thenReturn(List.of(registered("p.one", "Paul", "Partner")));
        when(userApiClient.getUserByUsername("p.one")).thenReturn(user("p.one", "Paul", "Partner", null));

        List<ParticipantRow> rows = collector.collect(EVENT_CODE);

        assertThat(rows).extracting(ParticipantRow::role).containsExactly(Role.ATTENDEE);
    }

    @Test
    @DisplayName("#1236: badge labels are the German role names, Partner included")
    void should_printGermanBadgeLabel_perRole() {
        assertThat(new ParticipantRow("a", "b", "c", Role.ORGANIZER).badgeLabel()).isEqualTo("Organisator");
        assertThat(new ParticipantRow("a", "b", "c", Role.SPEAKER).badgeLabel()).isEqualTo("Referent");
        assertThat(new ParticipantRow("a", "b", "c", Role.PARTNER).badgeLabel()).isEqualTo("Partner");
        assertThat(new ParticipantRow("a", "b", "c", Role.ATTENDEE).badgeLabel()).isEqualTo("Teilnehmer");
        assertThat(new ParticipantRow("", "", "", null).badgeLabel()).isEmpty();
    }

    private UserResponse user(String username, String firstName, String lastName, String companyId) {
        UserResponse u = new UserResponse()
                .id(username)
                .firstName(firstName)
                .lastName(lastName)
                .email(username + "@example.com");
        if (companyId != null) {
            u.companyId(companyId);
        }
        return u;
    }
}
