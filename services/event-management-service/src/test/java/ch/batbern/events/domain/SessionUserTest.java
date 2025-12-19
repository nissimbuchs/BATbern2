package ch.batbern.events.domain;

import ch.batbern.events.domain.SessionUser.SpeakerRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for SessionUser entity
 * Story 1.15a.1b: Session-User Many-to-Many Relationship - Task 10
 */
class SessionUserTest {

    private SessionUser sessionUser;
    private UUID sessionId;
    private String username;

    @BeforeEach
    void setUp() {
        sessionId = UUID.randomUUID();
        username = "test.user";

        Session session = Session.builder()
                .id(sessionId)
                .sessionSlug("test-session")
                .eventId(UUID.randomUUID())
                .title("Test Session")
                .sessionType("presentation")
                .startTime(Instant.now())
                .endTime(Instant.now().plusSeconds(3600))
                .build();

        sessionUser = SessionUser.builder()
                .session(session)
                .username(username)
                .speakerRole(SpeakerRole.PRIMARY_SPEAKER)
                .presentationTitle("Test Presentation")
                .isConfirmed(false)
                .build();
    }

    @Test
    void should_createSessionUser_when_validDataProvided() {
        // Given: valid SessionUser data in setUp()

        // Then: all fields should be set correctly
        assertThat(sessionUser.getSession().getId()).isEqualTo(sessionId);
        assertThat(sessionUser.getUsername()).isEqualTo(username);
        assertThat(sessionUser.getSpeakerRole()).isEqualTo(SpeakerRole.PRIMARY_SPEAKER);
        assertThat(sessionUser.getPresentationTitle()).isEqualTo("Test Presentation");
        assertThat(sessionUser.isConfirmed()).isFalse();
    }

    @Test
    void should_setTimestamps_when_prePersistCalled() {
        // Given: SessionUser without timestamps
        assertThat(sessionUser.getCreatedAt()).isNull();
        assertThat(sessionUser.getUpdatedAt()).isNull();
        assertThat(sessionUser.getInvitedAt()).isNull();

        // When: @PrePersist lifecycle hook is triggered
        sessionUser.onCreate();

        // Then: timestamps should be set
        assertThat(sessionUser.getCreatedAt()).isNotNull();
        assertThat(sessionUser.getUpdatedAt()).isNotNull();
        assertThat(sessionUser.getInvitedAt()).isNotNull();
    }

    @Test
    void should_updateTimestamp_when_preUpdateCalled() {
        // Given: SessionUser with initial timestamps
        sessionUser.onCreate();
        Instant initialUpdatedAt = sessionUser.getUpdatedAt();

        // When: @PreUpdate lifecycle hook is triggered
        try {
            Thread.sleep(10); // Ensure time difference
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        sessionUser.onUpdate();

        // Then: updatedAt should be newer
        assertThat(sessionUser.getUpdatedAt()).isAfterOrEqualTo(initialUpdatedAt);
    }

    @Test
    void should_confirmSpeaker_when_confirmMethodCalled() {
        // Given: Unconfirmed speaker
        assertThat(sessionUser.isConfirmed()).isFalse();
        assertThat(sessionUser.getConfirmedAt()).isNull();

        // When: confirm() is called
        sessionUser.confirm();

        // Then: speaker should be confirmed
        assertThat(sessionUser.isConfirmed()).isTrue();
        assertThat(sessionUser.getConfirmedAt()).isNotNull();
        assertThat(sessionUser.getDeclinedAt()).isNull();
        assertThat(sessionUser.getDeclineReason()).isNull();
    }

    @Test
    void should_clearDeclineData_when_confirmAfterDecline() {
        // Given: Speaker who previously declined
        sessionUser.decline("Schedule conflict");
        assertThat(sessionUser.isConfirmed()).isFalse();
        assertThat(sessionUser.getDeclinedAt()).isNotNull();
        assertThat(sessionUser.getDeclineReason()).isEqualTo("Schedule conflict");

        // When: confirm() is called
        sessionUser.confirm();

        // Then: decline data should be cleared
        assertThat(sessionUser.isConfirmed()).isTrue();
        assertThat(sessionUser.getConfirmedAt()).isNotNull();
        assertThat(sessionUser.getDeclinedAt()).isNull();
        assertThat(sessionUser.getDeclineReason()).isNull();
    }

    @Test
    void should_declineSpeaker_when_declineMethodCalled() {
        // Given: Unconfirmed speaker
        String reason = "Schedule conflict";

        // When: decline() is called with reason
        sessionUser.decline(reason);

        // Then: speaker should be declined
        assertThat(sessionUser.isConfirmed()).isFalse();
        assertThat(sessionUser.getDeclinedAt()).isNotNull();
        assertThat(sessionUser.getDeclineReason()).isEqualTo(reason);
        assertThat(sessionUser.getConfirmedAt()).isNull();
    }

    @Test
    void should_clearConfirmationData_when_declineAfterConfirm() {
        // Given: Speaker who was previously confirmed
        sessionUser.confirm();
        assertThat(sessionUser.isConfirmed()).isTrue();
        assertThat(sessionUser.getConfirmedAt()).isNotNull();

        // When: decline() is called
        sessionUser.decline("Changed mind");

        // Then: confirmation data should be cleared
        assertThat(sessionUser.isConfirmed()).isFalse();
        assertThat(sessionUser.getDeclinedAt()).isNotNull();
        assertThat(sessionUser.getDeclineReason()).isEqualTo("Changed mind");
        assertThat(sessionUser.getConfirmedAt()).isNull();
    }

    @Test
    void should_supportAllSpeakerRoles_when_creatingSessionUser() {
        // Test all speaker role enums
        SessionUser primarySpeaker = createSessionUserWithRole(SpeakerRole.PRIMARY_SPEAKER);
        SessionUser coSpeaker = createSessionUserWithRole(SpeakerRole.CO_SPEAKER);
        SessionUser moderator = createSessionUserWithRole(SpeakerRole.MODERATOR);
        SessionUser panelist = createSessionUserWithRole(SpeakerRole.PANELIST);

        assertThat(primarySpeaker.getSpeakerRole()).isEqualTo(SpeakerRole.PRIMARY_SPEAKER);
        assertThat(coSpeaker.getSpeakerRole()).isEqualTo(SpeakerRole.CO_SPEAKER);
        assertThat(moderator.getSpeakerRole()).isEqualTo(SpeakerRole.MODERATOR);
        assertThat(panelist.getSpeakerRole()).isEqualTo(SpeakerRole.PANELIST);
    }

    @Test
    void should_allowNullPresentationTitle_when_notProvided() {
        // Given: SessionUser without presentation title
        SessionUser sessionUserWithoutTitle = SessionUser.builder()
                .session(sessionUser.getSession())
                .username(username)
                .speakerRole(SpeakerRole.PRIMARY_SPEAKER)
                .isConfirmed(false)
                .build();

        // Then: presentationTitle should be null (optional field)
        assertThat(sessionUserWithoutTitle.getPresentationTitle()).isNull();
    }

    @Test
    void should_preserveInvitedAt_when_confirming() {
        // Given: SessionUser with invitedAt timestamp
        Instant invitedAt = Instant.now().minusSeconds(3600);
        sessionUser.setInvitedAt(invitedAt);

        // When: confirm() is called
        sessionUser.confirm();

        // Then: invitedAt should remain unchanged
        assertThat(sessionUser.getInvitedAt()).isEqualTo(invitedAt);
    }

    @Test
    void should_preserveInvitedAt_when_declining() {
        // Given: SessionUser with invitedAt timestamp
        Instant invitedAt = Instant.now().minusSeconds(3600);
        sessionUser.setInvitedAt(invitedAt);

        // When: decline() is called
        sessionUser.decline("Cannot attend");

        // Then: invitedAt should remain unchanged
        assertThat(sessionUser.getInvitedAt()).isEqualTo(invitedAt);
    }

    // Helper method
    private SessionUser createSessionUserWithRole(SpeakerRole role) {
        return SessionUser.builder()
                .session(sessionUser.getSession())
                .username(username)
                .speakerRole(role)
                .isConfirmed(false)
                .build();
    }
}
