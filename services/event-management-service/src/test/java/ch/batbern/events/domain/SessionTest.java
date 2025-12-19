package ch.batbern.events.domain;

import ch.batbern.events.domain.SessionUser.SpeakerRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for Session entity helper methods
 * Story 1.15a.1b: Session-User Many-to-Many Relationship - Task 11
 */
class SessionTest {

    private Session session;

    @BeforeEach
    void setUp() {
        session = Session.builder()
                .sessionSlug("test-session")
                .eventId(UUID.randomUUID())
                .title("Test Session")
                .description("Test Description")
                .sessionType("presentation")
                .startTime(Instant.now())
                .endTime(Instant.now().plusSeconds(3600))
                .room("Room A")
                .capacity(100)
                .language("de")
                .build();
    }

    @Test
    void should_addSpeaker_when_addSpeakerMethodCalled() {
        // Given: Session with no speakers
        assertThat(session.getSessionUsers()).isEmpty();

        // And: SessionUser to add
        SessionUser sessionUser = SessionUser.builder()
                .username("test-user")
                .speakerRole(SpeakerRole.PRIMARY_SPEAKER)
                .isConfirmed(false)
                .build();

        // When: addSpeaker() is called
        session.addSpeaker(sessionUser);

        // Then: speaker should be added and bidirectional reference set
        assertThat(session.getSessionUsers()).hasSize(1);
        assertThat(session.getSessionUsers()).contains(sessionUser);
        assertThat(sessionUser.getSession()).isEqualTo(session);
    }

    @Test
    void should_addMultipleSpeakers_when_addSpeakerCalledMultipleTimes() {
        // Given: Multiple SessionUsers
        SessionUser speaker1 = createSessionUser(SpeakerRole.PRIMARY_SPEAKER);
        SessionUser speaker2 = createSessionUser(SpeakerRole.CO_SPEAKER);
        SessionUser speaker3 = createSessionUser(SpeakerRole.MODERATOR);

        // When: addSpeaker() is called for each
        session.addSpeaker(speaker1);
        session.addSpeaker(speaker2);
        session.addSpeaker(speaker3);

        // Then: all speakers should be added
        assertThat(session.getSessionUsers()).hasSize(3);
        assertThat(session.getSessionUsers()).containsExactly(speaker1, speaker2, speaker3);
        assertThat(speaker1.getSession()).isEqualTo(session);
        assertThat(speaker2.getSession()).isEqualTo(session);
        assertThat(speaker3.getSession()).isEqualTo(session);
    }

    @Test
    void should_setBidirectionalReference_when_addSpeakerCalled() {
        // Given: SessionUser without session reference
        SessionUser sessionUser = createSessionUser(SpeakerRole.PRIMARY_SPEAKER);
        assertThat(sessionUser.getSession()).isNull();

        // When: addSpeaker() is called
        session.addSpeaker(sessionUser);

        // Then: bidirectional reference should be established
        assertThat(sessionUser.getSession()).isEqualTo(session);
        assertThat(session.getSessionUsers()).contains(sessionUser);
    }

    @Test
    void should_removeSpeaker_when_removeSpeakerMethodCalled() {
        // Given: Session with a speaker
        SessionUser sessionUser = createSessionUser(SpeakerRole.PRIMARY_SPEAKER);
        session.addSpeaker(sessionUser);
        assertThat(session.getSessionUsers()).hasSize(1);

        // When: removeSpeaker() is called
        session.removeSpeaker(sessionUser);

        // Then: speaker should be removed and reference cleared
        assertThat(session.getSessionUsers()).isEmpty();
        assertThat(sessionUser.getSession()).isNull();
    }

    @Test
    void should_removeOnlyTargetSpeaker_when_removeSpeakerCalledWithMultipleSpeakers() {
        // Given: Session with multiple speakers
        SessionUser speaker1 = createSessionUser(SpeakerRole.PRIMARY_SPEAKER);
        SessionUser speaker2 = createSessionUser(SpeakerRole.CO_SPEAKER);
        SessionUser speaker3 = createSessionUser(SpeakerRole.MODERATOR);
        session.addSpeaker(speaker1);
        session.addSpeaker(speaker2);
        session.addSpeaker(speaker3);
        assertThat(session.getSessionUsers()).hasSize(3);

        // When: removeSpeaker() is called for speaker2
        session.removeSpeaker(speaker2);

        // Then: only speaker2 should be removed
        assertThat(session.getSessionUsers()).hasSize(2);
        assertThat(session.getSessionUsers()).containsExactly(speaker1, speaker3);
        assertThat(speaker2.getSession()).isNull();
        assertThat(speaker1.getSession()).isEqualTo(session);
        assertThat(speaker3.getSession()).isEqualTo(session);
    }

    @Test
    void should_clearBidirectionalReference_when_removeSpeakerCalled() {
        // Given: Session with a speaker
        SessionUser sessionUser = createSessionUser(SpeakerRole.PRIMARY_SPEAKER);
        session.addSpeaker(sessionUser);
        assertThat(sessionUser.getSession()).isEqualTo(session);

        // When: removeSpeaker() is called
        session.removeSpeaker(sessionUser);

        // Then: bidirectional reference should be cleared
        assertThat(sessionUser.getSession()).isNull();
        assertThat(session.getSessionUsers()).doesNotContain(sessionUser);
    }

    @Test
    void should_handleRemoveNonExistentSpeaker_when_speakerNotInSession() {
        // Given: Session with one speaker
        SessionUser existingSpeaker = createSessionUser(SpeakerRole.PRIMARY_SPEAKER);
        session.addSpeaker(existingSpeaker);

        // And: Another speaker not in the session
        SessionUser nonExistentSpeaker = createSessionUser(SpeakerRole.CO_SPEAKER);

        // When: removeSpeaker() is called for non-existent speaker
        session.removeSpeaker(nonExistentSpeaker);

        // Then: existing speaker should remain, no exception thrown
        assertThat(session.getSessionUsers()).hasSize(1);
        assertThat(session.getSessionUsers()).contains(existingSpeaker);
    }

    @Test
    void should_initializeEmptySessionUsersList_when_sessionCreated() {
        // Given: Newly created session
        Session newSession = Session.builder()
                .sessionSlug("new-session")
                .eventId(UUID.randomUUID())
                .title("New Session")
                .sessionType("keynote")
                .startTime(Instant.now())
                .endTime(Instant.now().plusSeconds(3600))
                .build();

        // Then: sessionUsers list should be empty but not null
        assertThat(newSession.getSessionUsers()).isNotNull();
        assertThat(newSession.getSessionUsers()).isEmpty();
    }

    @Test
    void should_supportChainedOperations_when_addingAndRemovingSpeakers() {
        // Given: Multiple SessionUsers
        SessionUser speaker1 = createSessionUser(SpeakerRole.PRIMARY_SPEAKER);
        SessionUser speaker2 = createSessionUser(SpeakerRole.CO_SPEAKER);
        SessionUser speaker3 = createSessionUser(SpeakerRole.MODERATOR);

        // When: Chained add and remove operations
        session.addSpeaker(speaker1);
        session.addSpeaker(speaker2);
        session.addSpeaker(speaker3);
        session.removeSpeaker(speaker2);

        // Then: final state should be correct
        assertThat(session.getSessionUsers()).hasSize(2);
        assertThat(session.getSessionUsers()).containsExactly(speaker1, speaker3);
        assertThat(speaker1.getSession()).isEqualTo(session);
        assertThat(speaker2.getSession()).isNull();
        assertThat(speaker3.getSession()).isEqualTo(session);
    }

    // Helper method
    private SessionUser createSessionUser(SpeakerRole role) {
        return SessionUser.builder()
                .username("test-user")
                .speakerRole(role)
                .isConfirmed(false)
                .build();
    }
}
