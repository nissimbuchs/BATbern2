package ch.batbern.events.service;

import ch.batbern.events.client.UserApiClient;
import ch.batbern.events.dto.SpeakerDashboardDto;
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
import java.util.List;

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
        assertThat(dashboard.profileCompleteness()).isEqualTo(100);
        assertThat(dashboard.profilePictureUrl())
                .isEqualTo("https://cdn.batbern.ch/profiles/john.jpg");
        assertThat(dashboard.speakerName()).isEqualTo("John Doe");
        assertThat(dashboard.upcomingEvents()).isEmpty();
        assertThat(dashboard.pastEvents()).isEmpty();
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

        assertThat(dashboard.profileCompleteness()).isEqualTo(60);
        assertThat(dashboard.profilePictureUrl()).isNull();
    }

    @Test
    void should_fallBackToZeroCompletenessAndUsername_when_profileLookupFails() {
        when(sessionUserRepository.findByUsername("ghost.user")).thenReturn(List.of());
        when(userApiClient.getUserByUsername("ghost.user"))
                .thenThrow(new UserNotFoundException("ghost.user"));

        SpeakerDashboardDto dashboard = service.getDashboard("ghost.user");

        assertThat(dashboard.profileCompleteness()).isZero();
        assertThat(dashboard.speakerName()).isEqualTo("ghost.user");
        assertThat(dashboard.profilePictureUrl()).isNull();
    }
}
