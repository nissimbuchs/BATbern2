package ch.batbern.events.service;

import ch.batbern.events.client.UserApiClient;
import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.Session;
import ch.batbern.events.domain.SessionUser;
import ch.batbern.events.speakers.dto.generated.SpeakerDashboardDto;
import ch.batbern.events.dto.generated.users.UserResponse;
import ch.batbern.events.exception.UserNotFoundException;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.repository.SessionContentHistoryRepository;
import ch.batbern.events.repository.SessionMaterialsRepository;
import ch.batbern.events.repository.SessionRepository;
import ch.batbern.events.repository.SessionUserRepository;
import ch.batbern.events.repository.SpeakerPoolRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link SpeakerDashboardService}.
 *
 * 2026-06-05 regression: the empty-state dashboard (speaker with SPEAKER role but no
 * session_users rows — e.g. a freshly provisioned federated user) hardcoded
 * profileCompleteness=0 and dropped the profile picture, even though it already fetched
 * the CUMS profile for the display-name fallback. The speaker-portal dashboard then
 * showed 0% while /profile (computed client-side from the same five fields) showed 100%.
 */
@ExtendWith(MockitoExtension.class)
class SpeakerDashboardServiceTest {

    @Mock
    private SpeakerPoolRepository speakerPoolRepository;
    @Mock
    private EventRepository eventRepository;
    @Mock
    private SessionRepository sessionRepository;
    @Mock
    private SessionContentHistoryRepository sessionContentHistoryRepository;
    @Mock
    private SessionMaterialsRepository sessionMaterialsRepository;
    @Mock
    private SessionUserRepository sessionUserRepository;
    @Mock
    private UserApiClient userApiClient;

    private SpeakerDashboardService service;

    @BeforeEach
    void setUp() {
        service = new SpeakerDashboardService(
                speakerPoolRepository,
                eventRepository,
                sessionRepository,
                sessionContentHistoryRepository,
                sessionMaterialsRepository,
                sessionUserRepository,
                userApiClient);
    }

    @Test
    void should_resolveCompletenessAndPicture_when_speakerHasNoSessionMemberships() {
        when(sessionUserRepository.findByUsername("john.doe")).thenReturn(List.of());
        when(userApiClient.getUserByUsername("john.doe")).thenReturn(new UserResponse()
                .firstName("John")
                .lastName("Doe")
                .email("john.doe@example.com")
                .bio("Cloud architect")
                .profilePictureUrl(URI.create("https://cdn.batbern.ch/profiles/john.jpg")));

        SpeakerDashboardDto dashboard = service.getDashboard("john.doe");

        // All five completeness fields are filled → 100%, same as /profile shows.
        assertThat(dashboard.getProfileCompleteness()).isEqualTo(100);
        assertThat(dashboard.getProfilePictureUrl())
                .isEqualTo("https://cdn.batbern.ch/profiles/john.jpg");
        assertThat(dashboard.getSpeakerName()).isEqualTo("John Doe");
        assertThat(dashboard.getUpcomingEvents()).isEmpty();
        assertThat(dashboard.getPastEvents()).isEmpty();
    }

    @Test
    void should_reportPartialCompleteness_when_profileFieldsMissingAndNoMemberships() {
        when(sessionUserRepository.findByUsername("jane.new")).thenReturn(List.of());
        // 3 of 5 fields filled (firstName, lastName, email) → 60%.
        when(userApiClient.getUserByUsername("jane.new")).thenReturn(new UserResponse()
                .firstName("Jane")
                .lastName("New")
                .email("jane.new@example.com"));

        SpeakerDashboardDto dashboard = service.getDashboard("jane.new");

        assertThat(dashboard.getProfileCompleteness()).isEqualTo(60);
        assertThat(dashboard.getProfilePictureUrl()).isNull();
    }

    @Test
    void should_fallBackToZeroCompletenessAndUsername_when_profileLookupFails() {
        when(sessionUserRepository.findByUsername("ghost.user")).thenReturn(List.of());
        when(userApiClient.getUserByUsername("ghost.user"))
                .thenThrow(new UserNotFoundException("ghost.user"));

        SpeakerDashboardDto dashboard = service.getDashboard("ghost.user");

        assertThat(dashboard.getProfileCompleteness()).isZero();
        assertThat(dashboard.getSpeakerName()).isEqualTo("ghost.user");
        assertThat(dashboard.getProfilePictureUrl()).isNull();
    }

    /**
     * 2026-06-11 regression (Epic 7 testing): the dashboard was rerooted to session_users
     * (commit 7447d8fc) and now synthesizes {@code isConfirmed ? ACCEPTED : INVITED} when a
     * membership has no pool row. Migrated archive talks carry {@code is_confirmed = false}
     * and no pool row, so they synthesized to INVITED — which {@code PAST_STATES} excludes —
     * and silently vanished from "Past Events". A past, non-declined PRIMARY_SPEAKER (or
     * CO_SPEAKER / PANELIST) membership with no pool row IS a talk the speaker gave and must
     * appear regardless of {@code is_confirmed}.
     */
    @Test
    void should_includePastEvent_when_speakingMembershipHasNoPoolRow_andNotConfirmed() {
        UUID sessionId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        Session session = Session.builder()
                .id(sessionId)
                .eventId(eventId)
                .title("IBIS Desktop")
                .sessionType("presentation") // not a structural slot
                .speakerPoolId(null)          // migrated talk: no workflow pool row
                .build();
        Event event = Event.builder()
                .id(eventId)
                .eventCode("BATbern1")
                .title("BATbern 1")
                .date(Instant.parse("2005-06-24T14:00:00Z")) // past
                .build();
        SessionUser membership = SessionUser.builder()
                .session(session)
                .username("nissim.buchs")
                .speakerRole(SessionUser.SpeakerRole.PRIMARY_SPEAKER)
                .isConfirmed(false) // migrated data never set the forward-workflow flag
                .build();

        when(sessionUserRepository.findByUsername("nissim.buchs")).thenReturn(List.of(membership));
        when(sessionRepository.findAllById(Set.of(sessionId))).thenReturn(List.of(session));
        when(eventRepository.findAllById(Set.of(eventId))).thenReturn(List.of(event));
        // PRIMARY_SPEAKER + null speakerPoolId → reverse lookup, also empty for migrated data.
        when(speakerPoolRepository.findBySessionId(sessionId)).thenReturn(List.of());
        when(sessionMaterialsRepository.existsBySession_IdAndMaterialType(sessionId, "PRESENTATION"))
                .thenReturn(false);
        when(userApiClient.getUserByUsername("nissim.buchs")).thenReturn(new UserResponse()
                .firstName("Nissim").lastName("Buchs"));

        SpeakerDashboardDto dashboard = service.getDashboard("nissim.buchs");

        assertThat(dashboard.getPastEvents()).hasSize(1);
        assertThat(dashboard.getPastEvents().get(0).getEventCode()).isEqualTo("BATbern1");
        assertThat(dashboard.getPastEvents().get(0).getSessionTitle()).isEqualTo("IBIS Desktop");
        assertThat(dashboard.getUpcomingEvents()).isEmpty();
    }

    /**
     * Counterpart to the regression above: a no-pool MODERATOR membership on a
     * presentation-typed "Programmheft" row is a migration artifact (program-booklet
     * catalog entry), NOT a talk the speaker gave. "Where I was speaker" must not
     * resurface these, so only speaking roles (PRIMARY_SPEAKER / CO_SPEAKER / PANELIST)
     * are restored for pool-less past memberships.
     */
    @Test
    void should_excludePastEvent_when_noPoolMembershipIsModeratorArtifact() {
        UUID sessionId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        Session session = Session.builder()
                .id(sessionId)
                .eventId(eventId)
                .title("Programmheft")
                .sessionType("presentation") // not structural, so not filtered by slot type
                .speakerPoolId(null)
                .build();
        Event event = Event.builder()
                .id(eventId)
                .eventCode("BATbern10")
                .title("BATbern 10")
                .date(Instant.parse("2008-06-20T06:30:00Z")) // past
                .build();
        SessionUser membership = SessionUser.builder()
                .session(session)
                .username("nissim.buchs")
                .speakerRole(SessionUser.SpeakerRole.MODERATOR)
                .isConfirmed(false)
                .build();

        when(sessionUserRepository.findByUsername("nissim.buchs")).thenReturn(List.of(membership));
        when(sessionRepository.findAllById(Set.of(sessionId))).thenReturn(List.of(session));
        when(eventRepository.findAllById(Set.of(eventId))).thenReturn(List.of(event));
        when(userApiClient.getUserByUsername("nissim.buchs")).thenReturn(new UserResponse()
                .firstName("Nissim").lastName("Buchs"));

        SpeakerDashboardDto dashboard = service.getDashboard("nissim.buchs");

        assertThat(dashboard.getPastEvents()).isEmpty();
        assertThat(dashboard.getUpcomingEvents()).isEmpty();
    }
}
