package ch.batbern.events.repository;

import ch.batbern.events.AbstractIntegrationTest;
import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.Session;
import ch.batbern.events.domain.SessionUser;
import ch.batbern.events.domain.SessionUser.SpeakerRole;
import ch.batbern.events.dto.generated.EventType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for SessionUserRepository using Testcontainers PostgreSQL
 * Story 1.15a.1b: Session-User Many-to-Many Relationship - Task 12
 *
 * CRITICAL: Uses real PostgreSQL via Testcontainers (NOT H2) for production parity
 */
@Transactional
class SessionUserRepositoryIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private SessionUserRepository sessionUserRepository;

    @Autowired
    private SessionRepository sessionRepository;

    @Autowired
    private EventRepository eventRepository;

    private Event testEvent;
    private Session testSession1;
    private Session testSession2;
    private String username1;
    private String username2;
    private String username3;

    @BeforeEach
    void setUp() {
        // Create test event
        testEvent = Event.builder()
                .eventCode("BATbern999")
                .eventNumber(999)
                .title("Test Event")
                .description("Test Description")
                .date(Instant.now().plusSeconds(86400))
                .registrationDeadline(Instant.now())
                .venueName("Test Venue")
                .venueAddress("Test Address")
                .venueCapacity(100)
                .organizerUsername("test.organizer")
                .eventType(EventType.EVENING)
                .workflowState(ch.batbern.shared.types.EventWorkflowState.CREATED)
                .build();
        testEvent = eventRepository.save(testEvent);

        // Create test sessions
        testSession1 = Session.builder()
                .sessionSlug("session-1")
                .eventId(testEvent.getId())
                .title("Session 1")
                .sessionType("presentation")
                .startTime(Instant.now())
                .endTime(Instant.now().plusSeconds(3600))
                .build();
        testSession1 = sessionRepository.save(testSession1);

        testSession2 = Session.builder()
                .sessionSlug("session-2")
                .eventId(testEvent.getId())
                .title("Session 2")
                .sessionType("workshop")
                .startTime(Instant.now().plusSeconds(7200))
                .endTime(Instant.now().plusSeconds(10800))
                .build();
        testSession2 = sessionRepository.save(testSession2);

        // Test usernames (simulating users from user-profiles table)
        username1 = "john.doe";
        username2 = "jane.smith";
        username3 = "bob.wilson";
    }

    @Test
    void should_saveSessionUser_when_validDataProvided() {
        // Given: Valid SessionUser
        SessionUser sessionUser = SessionUser.builder()
                .session(testSession1)
                .username("test-user")
                .speakerRole(SpeakerRole.PRIMARY_SPEAKER)
                .presentationTitle("Test Presentation")
                .isConfirmed(false)
                .build();

        // When: Saving SessionUser
        SessionUser saved = sessionUserRepository.save(sessionUser);

        // Then: Should be persisted with generated ID
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getSession().getId()).isEqualTo(testSession1.getId());
        assertThat(saved.getUsername()).isEqualTo("test-user");
        assertThat(saved.getSpeakerRole()).isEqualTo(SpeakerRole.PRIMARY_SPEAKER);
    }

    @Test
    void should_findBySessionId_when_speakersAssigned() {
        // Given: Session with multiple speakers
        createSessionUser(testSession1, username1, SpeakerRole.PRIMARY_SPEAKER);
        createSessionUser(testSession1, username2, SpeakerRole.CO_SPEAKER);
        createSessionUser(testSession2, username3, SpeakerRole.MODERATOR);

        // When: Finding by session1 ID
        List<SessionUser> speakers = sessionUserRepository.findBySessionId(testSession1.getId());

        // Then: Should return only session1 speakers
        assertThat(speakers).hasSize(2);
        assertThat(speakers).extracting(SessionUser::getUsername)
                .containsExactlyInAnyOrder(username1, username2);
    }

    @Test
    void should_findByUsername_when_userAssignedToMultipleSessions() {
        // Given: User assigned to multiple sessions
        createSessionUser(testSession1, username1, SpeakerRole.PRIMARY_SPEAKER);
        createSessionUser(testSession2, username1, SpeakerRole.CO_SPEAKER);
        createSessionUser(testSession2, username2, SpeakerRole.MODERATOR);

        // When: Finding by user1 ID
        List<SessionUser> sessions = sessionUserRepository.findByUsername(username1);

        // Then: Should return all sessions for user1
        assertThat(sessions).hasSize(2);
        assertThat(sessions).extracting(su -> su.getSession().getId())
                .containsExactlyInAnyOrder(testSession1.getId(), testSession2.getId());
    }

    @Test
    void should_findBySessionIdAndIsConfirmedTrue_when_onlyConfirmedSpeakers() {
        // Given: Session with confirmed and unconfirmed speakers
        SessionUser confirmed1 = createSessionUser(testSession1, username1, SpeakerRole.PRIMARY_SPEAKER);
        confirmed1.confirm();
        sessionUserRepository.save(confirmed1);

        SessionUser unconfirmed = createSessionUser(testSession1, username2, SpeakerRole.CO_SPEAKER);
        // Leave unconfirmed

        SessionUser confirmed2 = createSessionUser(testSession1, username3, SpeakerRole.PANELIST);
        confirmed2.confirm();
        sessionUserRepository.save(confirmed2);

        // When: Finding only confirmed speakers
        List<SessionUser> confirmedSpeakers = sessionUserRepository
                .findBySessionIdAndIsConfirmedTrue(testSession1.getId());

        // Then: Should return only confirmed speakers
        assertThat(confirmedSpeakers).hasSize(2);
        assertThat(confirmedSpeakers).extracting(SessionUser::getUsername)
                .containsExactlyInAnyOrder(username1, username3);
        assertThat(confirmedSpeakers).allMatch(SessionUser::isConfirmed);
    }

    @Test
    void should_findBySessionIdAndUsername_when_specificAssignmentExists() {
        // Given: Multiple speaker assignments
        createSessionUser(testSession1, username1, SpeakerRole.PRIMARY_SPEAKER);
        createSessionUser(testSession1, username2, SpeakerRole.CO_SPEAKER);

        // When: Finding specific assignment
        Optional<SessionUser> found = sessionUserRepository
                .findBySessionIdAndUsername(testSession1.getId(), username1);

        // Then: Should return the specific assignment
        assertThat(found).isPresent();
        assertThat(found.get().getUsername()).isEqualTo(username1);
        assertThat(found.get().getSpeakerRole()).isEqualTo(SpeakerRole.PRIMARY_SPEAKER);
    }

    @Test
    void should_returnEmpty_when_assignmentDoesNotExist() {
        // Given: Session with speaker
        createSessionUser(testSession1, username1, SpeakerRole.PRIMARY_SPEAKER);

        // When: Finding non-existent assignment
        Optional<SessionUser> found = sessionUserRepository
                .findBySessionIdAndUsername(testSession1.getId(), username2);

        // Then: Should return empty
        assertThat(found).isEmpty();
    }

    @Test
    void should_returnTrue_when_assignmentExists() {
        // Given: Speaker assignment
        createSessionUser(testSession1, username1, SpeakerRole.PRIMARY_SPEAKER);

        // When: Checking if assignment exists
        boolean exists = sessionUserRepository
                .existsBySessionIdAndUsername(testSession1.getId(), username1);

        // Then: Should return true
        assertThat(exists).isTrue();
    }

    @Test
    void should_returnFalse_when_assignmentDoesNotExist() {
        // Given: No assignment for user2
        createSessionUser(testSession1, username1, SpeakerRole.PRIMARY_SPEAKER);

        // When: Checking if assignment exists for user2
        boolean exists = sessionUserRepository
                .existsBySessionIdAndUsername(testSession1.getId(), username2);

        // Then: Should return false
        assertThat(exists).isFalse();
    }

    @Test
    void should_findAllByEventId_when_eventHasMultipleSessions() {
        // Given: Event with multiple sessions and speakers
        createSessionUser(testSession1, username1, SpeakerRole.PRIMARY_SPEAKER);
        createSessionUser(testSession1, username2, SpeakerRole.CO_SPEAKER);
        createSessionUser(testSession2, username2, SpeakerRole.MODERATOR);
        createSessionUser(testSession2, username3, SpeakerRole.PANELIST);

        // When: Finding all speakers for event
        List<SessionUser> eventSpeakers = sessionUserRepository.findAllByEventId(testEvent.getId());

        // Then: Should return all speakers from all sessions
        assertThat(eventSpeakers).hasSize(4);
        assertThat(eventSpeakers).extracting(SessionUser::getUsername)
                .containsExactlyInAnyOrder(username1, username2, username2, username3);
    }

    @Test
    void should_countBySessionId_when_multipleSpeakersAssigned() {
        // Given: Session with 3 speakers
        createSessionUser(testSession1, username1, SpeakerRole.PRIMARY_SPEAKER);
        createSessionUser(testSession1, username2, SpeakerRole.CO_SPEAKER);
        createSessionUser(testSession1, username3, SpeakerRole.MODERATOR);

        // When: Counting speakers
        long count = sessionUserRepository.countBySessionId(testSession1.getId());

        // Then: Should return correct count
        assertThat(count).isEqualTo(3);
    }

    @Test
    void should_enforceUniqueConstraint_when_duplicateAssignment() {
        // Given: Existing assignment
        createSessionUser(testSession1, username1, SpeakerRole.PRIMARY_SPEAKER);

        // When: Attempting duplicate assignment
        SessionUser duplicate = SessionUser.builder()
                .session(testSession1)
                .username("test-user")
                .speakerRole(SpeakerRole.CO_SPEAKER)
                .isConfirmed(false)
                .build();

        // Then: Should throw exception (unique constraint violation)
        try {
            sessionUserRepository.saveAndFlush(duplicate);
            assertThat(false).as("Should have thrown exception for duplicate assignment").isTrue();
        } catch (Exception e) {
            assertThat(e).hasMessageContaining("unique_session_user")
                    .describedAs("Should violate unique constraint on (session_id, user_id)");
        }
    }

    @Test
    void should_cascadeDelete_when_sessionDeleted() {
        // Given: Session with speakers - manually maintain bidirectional relationship for cascade
        SessionUser su1 = createSessionUser(testSession1, username1, SpeakerRole.PRIMARY_SPEAKER);
        SessionUser su2 = createSessionUser(testSession1, username2, SpeakerRole.CO_SPEAKER);
        testSession1.getSessionUsers().add(su1);
        testSession1.getSessionUsers().add(su2);
        sessionRepository.save(testSession1);
        assertThat(sessionUserRepository.findBySessionId(testSession1.getId())).hasSize(2);

        // When: Deleting session
        sessionRepository.delete(testSession1);
        sessionRepository.flush();

        // Then: SessionUsers should be cascade deleted
        List<SessionUser> remaining = sessionUserRepository.findBySessionId(testSession1.getId());
        assertThat(remaining).isEmpty();
    }

    @Test
    void should_deleteBySessionIdAndUsername_when_removingSingleSpeaker() {
        // Given: Session with multiple speakers
        createSessionUser(testSession1, username1, SpeakerRole.PRIMARY_SPEAKER);
        createSessionUser(testSession1, username2, SpeakerRole.CO_SPEAKER);
        createSessionUser(testSession1, username3, SpeakerRole.MODERATOR);

        // When: Deleting specific speaker
        sessionUserRepository.deleteBySessionIdAndUsername(testSession1.getId(), username2);
        sessionUserRepository.flush();

        // Then: Only target speaker should be deleted
        List<SessionUser> remaining = sessionUserRepository.findBySessionId(testSession1.getId());
        assertThat(remaining).hasSize(2);
        assertThat(remaining).extracting(SessionUser::getUsername)
                .containsExactlyInAnyOrder(username1, username3);
    }

    // Helper method
    private SessionUser createSessionUser(Session session, String username, SpeakerRole role) {
        SessionUser sessionUser = SessionUser.builder()
                .session(session)
                .username(username)
                .speakerRole(role)
                .isConfirmed(false)
                .build();
        return sessionUserRepository.save(sessionUser);
    }
}
