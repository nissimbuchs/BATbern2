package ch.batbern.events.service;

import ch.batbern.events.client.UserApiClient;
import ch.batbern.events.domain.Session;
import ch.batbern.events.domain.SessionUser;
import ch.batbern.events.domain.SessionUser.SpeakerRole;
import ch.batbern.events.dto.SessionSpeakerResponse;
import ch.batbern.events.dto.generated.users.UserResponse;
import ch.batbern.events.exception.SpeakerAssignmentNotFoundException;
import ch.batbern.events.exception.UserNotFoundException;
import ch.batbern.events.repository.SessionRepository;
import ch.batbern.events.repository.SessionUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for SessionUserService with mocked dependencies
 * Story 1.15a.1b: Session-User Many-to-Many Relationship - Task 13
 *
 * Updated to use UserApiClient instead of direct database access
 */
@ExtendWith(MockitoExtension.class)
class SessionUserServiceTest {

    @Mock
    private SessionUserRepository sessionUserRepository;

    @Mock
    private SessionRepository sessionRepository;

    @Mock
    private UserApiClient userApiClient;

    @InjectMocks
    private SessionUserService sessionUserService;

    private Session testSession;
    private UserResponse testUser;
    private UUID sessionId;
    private UUID userId;
    private String username;

    @BeforeEach
    void setUp() {
        sessionId = UUID.randomUUID();
        userId = UUID.randomUUID();
        username = "john.doe";

        testSession = Session.builder()
                .id(sessionId)
                .sessionSlug("test-session")
                .eventId(UUID.randomUUID())
                .title("Test Session")
                .sessionType("presentation")
                .startTime(Instant.now())
                .endTime(Instant.now().plusSeconds(3600))
                .build();

        testUser = new UserResponse()
                .id(username)
                .email("john.doe@example.com")
                .firstName("John")
                .lastName("Doe")
                .companyId("GoogleZH")
                .profilePictureUrl(java.net.URI.create("https://example.com/photo.jpg"))
                .active(true)
                ;
    }

    @Test
    void should_assignSpeakerToSession_when_validDataProvided() {
        // Given: Valid session and user exist
        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(testSession));
        when(userApiClient.getUserByUsername(username)).thenReturn(testUser);
        when(sessionUserRepository.existsBySessionIdAndUsername(sessionId, username)).thenReturn(false);

        SessionUser savedSessionUser = SessionUser.builder()
                .id(UUID.randomUUID())
                .session(testSession)
                .userId(userId)
                .username(username)
                .speakerRole(SpeakerRole.PRIMARY_SPEAKER)
                .presentationTitle("Test Presentation")
                .isConfirmed(false)
                .build();
        when(sessionUserRepository.save(any(SessionUser.class))).thenReturn(savedSessionUser);

        // When: Assigning speaker to session
        SessionSpeakerResponse response = sessionUserService.assignSpeakerToSession(
                sessionId,
                username,
                SpeakerRole.PRIMARY_SPEAKER,
                "Test Presentation"
        );

        // Then: SessionUser should be created and saved
        ArgumentCaptor<SessionUser> captor = ArgumentCaptor.forClass(SessionUser.class);
        verify(sessionUserRepository).save(captor.capture());

        SessionUser captured = captor.getValue();
        assertThat(captured.getSession()).isEqualTo(testSession);
        // userId is generated deterministically from username for backward compat
        UUID expectedUserId = UUID.nameUUIDFromBytes(("user:" + username).getBytes());
        assertThat(captured.getUserId()).isEqualTo(expectedUserId);
        assertThat(captured.getUsername()).isEqualTo(username);
        assertThat(captured.getSpeakerRole()).isEqualTo(SpeakerRole.PRIMARY_SPEAKER);
        assertThat(captured.getPresentationTitle()).isEqualTo("Test Presentation");
        assertThat(captured.isConfirmed()).isFalse();

        // And: Response should contain enriched user data
        assertThat(response.getUsername()).isEqualTo(username);
        assertThat(response.getFirstName()).isEqualTo("John");
        assertThat(response.getLastName()).isEqualTo("Doe");
        assertThat(response.getCompany()).isEqualTo("GoogleZH");
        assertThat(response.getSpeakerRole()).isEqualTo(SpeakerRole.PRIMARY_SPEAKER);
    }

    @Test
    void should_throwException_when_sessionNotFound() {
        // Given: Session does not exist
        when(sessionRepository.findById(sessionId)).thenReturn(Optional.empty());

        // When/Then: Should throw IllegalArgumentException
        assertThatThrownBy(() -> sessionUserService.assignSpeakerToSession(
                sessionId, username, SpeakerRole.PRIMARY_SPEAKER, null
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Session not found");

        verify(sessionUserRepository, never()).save(any());
    }

    @Test
    void should_throwException_when_userNotFound() {
        // Given: Session exists but user does not
        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(testSession));
        when(userApiClient.getUserByUsername(username)).thenThrow(new UserNotFoundException(username));

        // When/Then: Should throw UserNotFoundException
        assertThatThrownBy(() -> sessionUserService.assignSpeakerToSession(
                sessionId, username, SpeakerRole.PRIMARY_SPEAKER, null
        ))
                .isInstanceOf(UserNotFoundException.class)
                .hasMessageContaining("User not found");

        verify(sessionUserRepository, never()).save(any());
    }

    @Test
    void should_throwException_when_duplicateAssignment() {
        // Given: Session and user exist, but speaker already assigned
        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(testSession));
        when(userApiClient.getUserByUsername(username)).thenReturn(testUser);
        when(sessionUserRepository.existsBySessionIdAndUsername(sessionId, username)).thenReturn(true);

        // When/Then: Should throw IllegalArgumentException
        assertThatThrownBy(() -> sessionUserService.assignSpeakerToSession(
                sessionId, username, SpeakerRole.PRIMARY_SPEAKER, null
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already assigned");

        verify(sessionUserRepository, never()).save(any());
    }

    @Test
    void should_removeSpeakerFromSession_when_assignmentExists() {
        // Given: Speaker assignment exists
        SessionUser sessionUser = SessionUser.builder()
                .id(UUID.randomUUID())
                .session(testSession)
                .userId(userId)
                .username(username)
                .speakerRole(SpeakerRole.PRIMARY_SPEAKER)
                .build();
        when(sessionUserRepository.findBySessionIdAndUsername(sessionId, username))
                .thenReturn(Optional.of(sessionUser));

        // When: Removing speaker from session
        sessionUserService.removeSpeakerFromSession(sessionId, username);

        // Then: SessionUser should be deleted
        verify(sessionUserRepository).delete(sessionUser);
    }

    @Test
    void should_throwException_when_removingNonExistentAssignment() {
        // Given: No assignment exists
        when(sessionUserRepository.findBySessionIdAndUsername(sessionId, username))
                .thenReturn(Optional.empty());

        // When/Then: Should throw SpeakerAssignmentNotFoundException
        assertThatThrownBy(() -> sessionUserService.removeSpeakerFromSession(sessionId, username))
                .isInstanceOf(SpeakerAssignmentNotFoundException.class)
                .hasMessageContaining("Speaker assignment not found");

        verify(sessionUserRepository, never()).delete(any());
    }

    @Test
    void should_confirmSpeaker_when_assignmentExists() {
        // Given: Speaker assignment exists
        when(userApiClient.getUserByUsername(username)).thenReturn(testUser);

        SessionUser sessionUser = SessionUser.builder()
                .id(UUID.randomUUID())
                .session(testSession)
                .userId(userId)
                .username(username)
                .speakerRole(SpeakerRole.PRIMARY_SPEAKER)
                .isConfirmed(false)
                .build();
        when(sessionUserRepository.findBySessionIdAndUsername(sessionId, username))
                .thenReturn(Optional.of(sessionUser));
        when(sessionUserRepository.save(any(SessionUser.class))).thenReturn(sessionUser);

        // When: Confirming speaker
        SessionSpeakerResponse response = sessionUserService.confirmSpeaker(sessionId, username);

        // Then: SessionUser should be updated with confirmed status
        verify(sessionUserRepository).save(sessionUser);
        assertThat(sessionUser.isConfirmed()).isTrue();
        assertThat(sessionUser.getConfirmedAt()).isNotNull();

        // And: Response should reflect confirmation
        assertThat(response.isConfirmed()).isTrue();
    }

    @Test
    void should_declineSpeaker_when_assignmentExists() {
        // Given: Speaker assignment exists
        when(userApiClient.getUserByUsername(username)).thenReturn(testUser);

        SessionUser sessionUser = SessionUser.builder()
                .id(UUID.randomUUID())
                .session(testSession)
                .userId(userId)
                .username(username)
                .speakerRole(SpeakerRole.PRIMARY_SPEAKER)
                .isConfirmed(false)
                .build();
        when(sessionUserRepository.findBySessionIdAndUsername(sessionId, username))
                .thenReturn(Optional.of(sessionUser));
        when(sessionUserRepository.save(any(SessionUser.class))).thenReturn(sessionUser);

        String declineReason = "Schedule conflict";

        // When: Declining speaker
        SessionSpeakerResponse response = sessionUserService.declineSpeaker(
                sessionId, username, declineReason
        );

        // Then: SessionUser should be updated with decline information
        verify(sessionUserRepository).save(sessionUser);
        assertThat(sessionUser.isConfirmed()).isFalse();
        assertThat(sessionUser.getDeclinedAt()).isNotNull();
        assertThat(sessionUser.getDeclineReason()).isEqualTo(declineReason);

        // And: Response should reflect decline
        assertThat(response.isConfirmed()).isFalse();
    }

    @Test
    void should_getSessionSpeakers_when_speakersExist() {
        // Given: Session has multiple speakers
        SessionUser speaker1 = SessionUser.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .username(username)
                .speakerRole(SpeakerRole.PRIMARY_SPEAKER)
                .isConfirmed(true)
                .build();

        UUID userId2 = UUID.randomUUID();
        String username2 = "jane.smith";
        UserResponse user2 = new UserResponse()
                .id(username2)
                .email("jane.smith@example.com")
                .firstName("Jane")
                .lastName("Smith")
                .companyId("MicrosoftBE")
                .profilePictureUrl(java.net.URI.create("https://example.com/jane.jpg"))
                .active(true)
                ;

        SessionUser speaker2 = SessionUser.builder()
                .id(UUID.randomUUID())
                .userId(userId2)
                .username(username2)
                .speakerRole(SpeakerRole.CO_SPEAKER)
                .isConfirmed(false)
                .build();

        when(sessionUserRepository.findBySessionId(sessionId))
                .thenReturn(List.of(speaker1, speaker2));
        when(userApiClient.getUserByUsername(username)).thenReturn(testUser);
        when(userApiClient.getUserByUsername(username2)).thenReturn(user2);

        // When: Getting session speakers
        List<SessionSpeakerResponse> speakers = sessionUserService.getSessionSpeakers(sessionId);

        // Then: Should return enriched speaker data
        assertThat(speakers).hasSize(2);

        SessionSpeakerResponse firstSpeaker = speakers.get(0);
        assertThat(firstSpeaker.getUsername()).isEqualTo("john.doe");
        assertThat(firstSpeaker.getFirstName()).isEqualTo("John");
        assertThat(firstSpeaker.getLastName()).isEqualTo("Doe");
        assertThat(firstSpeaker.getCompany()).isEqualTo("GoogleZH");
        assertThat(firstSpeaker.getSpeakerRole()).isEqualTo(SpeakerRole.PRIMARY_SPEAKER);
        assertThat(firstSpeaker.isConfirmed()).isTrue();

        SessionSpeakerResponse secondSpeaker = speakers.get(1);
        assertThat(secondSpeaker.getUsername()).isEqualTo("jane.smith");
        assertThat(secondSpeaker.getSpeakerRole()).isEqualTo(SpeakerRole.CO_SPEAKER);
        assertThat(secondSpeaker.isConfirmed()).isFalse();
    }

    @Test
    void should_returnEmptyList_when_noSpeakersAssigned() {
        // Given: Session has no speakers
        when(sessionUserRepository.findBySessionId(sessionId)).thenReturn(List.of());

        // When: Getting session speakers
        List<SessionSpeakerResponse> speakers = sessionUserService.getSessionSpeakers(sessionId);

        // Then: Should return empty list
        assertThat(speakers).isEmpty();
    }

    @Test
    void should_getEventSpeakers_when_eventHasMultipleSessions() {
        // Given: Event with multiple sessions and speakers
        UUID eventId = UUID.randomUUID();

        SessionUser speaker1 = SessionUser.builder()
                .userId(userId)
                .username(username)
                .speakerRole(SpeakerRole.PRIMARY_SPEAKER)
                .build();

        when(sessionUserRepository.findAllByEventId(eventId)).thenReturn(List.of(speaker1));
        when(userApiClient.getUserByUsername(username)).thenReturn(testUser);

        // When: Getting event speakers
        List<SessionSpeakerResponse> speakers = sessionUserService.getEventSpeakers(eventId);

        // Then: Should return all speakers for the event
        assertThat(speakers).hasSize(1);
        assertThat(speakers.get(0).getUsername()).isEqualTo("john.doe");
    }

    @Test
    void should_handleNullPresentationTitle_when_assigning() {
        // Given: Valid session and user, no presentation title
        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(testSession));
        when(userApiClient.getUserByUsername(username)).thenReturn(testUser);
        when(sessionUserRepository.existsBySessionIdAndUsername(sessionId, username)).thenReturn(false);

        SessionUser savedSessionUser = SessionUser.builder()
                .id(UUID.randomUUID())
                .session(testSession)
                .userId(userId)
                .username(username)
                .speakerRole(SpeakerRole.MODERATOR)
                .presentationTitle(null)
                .isConfirmed(false)
                .build();
        when(sessionUserRepository.save(any(SessionUser.class))).thenReturn(savedSessionUser);

        // When: Assigning speaker without presentation title
        SessionSpeakerResponse response = sessionUserService.assignSpeakerToSession(
                sessionId,
                username,
                SpeakerRole.MODERATOR,
                null
        );

        // Then: Should succeed with null presentationTitle
        assertThat(response.getPresentationTitle()).isNull();
        assertThat(response.getSpeakerRole()).isEqualTo(SpeakerRole.MODERATOR);
    }
}
