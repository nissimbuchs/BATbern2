package ch.batbern.events.domain;

import ch.batbern.events.AbstractIntegrationTest;
import ch.batbern.events.repository.SpeakerInvitationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for SpeakerInvitation entity - Story 6.1.
 *
 * Tests SpeakerInvitation JPA entity with PostgreSQL via Testcontainers.
 * Validates ADR-003/ADR-004 compliance: username/event_code references, no cross-service FK.
 *
 * Test naming convention: should_expectedBehavior_when_condition
 */
@Transactional
class SpeakerInvitationTest extends AbstractIntegrationTest {

    @Autowired
    private SpeakerInvitationRepository invitationRepository;

    // AC1 Tests: SpeakerInvitation Entity

    @Test
    void should_createInvitationEntity_when_validUsernameAndEventProvided() {
        // Given - ADR-003: Use username and event_code as meaningful identifiers
        // Token must be exactly 64 characters
        SpeakerInvitation invitation = SpeakerInvitation.builder()
                .username("john.doe")
                .eventCode("BAT-2026-Q1")
                .responseToken("tkn12345678901234567890123456789012345678901234567890123456789012")
                .expiresAt(Instant.now().plus(14, ChronoUnit.DAYS))
                .createdBy("organizer.smith")
                .build();

        // When
        SpeakerInvitation saved = invitationRepository.save(invitation);

        // Then
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getUsername()).isEqualTo("john.doe");
        assertThat(saved.getEventCode()).isEqualTo("BAT-2026-Q1");
        assertThat(saved.getInvitationStatus()).isEqualTo(InvitationStatus.PENDING);
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
    }

    @Test
    void should_generateUniqueToken_when_invitationCreated() {
        // Given - 64 character token
        String token = "abc1234567890123456789012345678901234567890123456789012345678901";
        SpeakerInvitation invitation = createTestInvitation("speaker1", "EVT-001", token);

        // When
        SpeakerInvitation saved = invitationRepository.save(invitation);
        invitationRepository.flush();

        // Then
        assertThat(saved.getResponseToken()).isEqualTo(token);
        assertThat(saved.getResponseToken()).hasSize(64);
    }

    @Test
    void should_enforceUniqueConstraint_when_duplicateTokenProvided() {
        // Given - Token must be unique (exactly 64 chars)
        String duplicateToken = "dup1234567890123456789012345678901234567890123456789012345678901";
        invitationRepository.save(createTestInvitation("speaker1", "EVT-001", duplicateToken));
        invitationRepository.flush();

        // When/Then
        assertThatThrownBy(() -> {
            invitationRepository.save(createTestInvitation("speaker2", "EVT-002", duplicateToken));
            invitationRepository.flush();
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void should_enforceUniquePerEventConstraint_when_duplicateInvitationToSameSpeakerAndEvent() {
        // Given - Only one active invitation per speaker per event (64 char tokens)
        String token1 = "tok1a34567890123456789012345678901234567890123456789012345678901";
        String token2 = "tok2b34567890123456789012345678901234567890123456789012345678901";

        invitationRepository.save(createTestInvitation("speaker1", "EVT-001", token1));
        invitationRepository.flush();

        // When/Then - Second invitation to same speaker for same event should fail
        assertThatThrownBy(() -> {
            invitationRepository.save(createTestInvitation("speaker1", "EVT-001", token2));
            invitationRepository.flush();
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void should_allowMultipleInvitations_when_differentEvents() {
        // Given - 64 character tokens
        String token1 = "evn1a34567890123456789012345678901234567890123456789012345678901";
        String token2 = "evn2b34567890123456789012345678901234567890123456789012345678901";

        // When - Same speaker, different events
        SpeakerInvitation inv1 = invitationRepository.save(createTestInvitation("speaker1", "EVT-001", token1));
        SpeakerInvitation inv2 = invitationRepository.save(createTestInvitation("speaker1", "EVT-002", token2));
        invitationRepository.flush();

        // Then
        assertThat(inv1.getId()).isNotEqualTo(inv2.getId());
    }

    // AC6 Tests: Status Tracking

    @Test
    void should_trackInvitationStatus_when_statusChanges() {
        // Given
        SpeakerInvitation invitation = createTestInvitation("status.test", "EVT-STATUS", generateUniqueToken());
        SpeakerInvitation saved = invitationRepository.save(invitation);
        assertThat(saved.getInvitationStatus()).isEqualTo(InvitationStatus.PENDING);

        // When - status progression
        saved.setInvitationStatus(InvitationStatus.SENT);
        saved.setSentAt(Instant.now());
        invitationRepository.save(saved);
        invitationRepository.flush();

        // Then
        SpeakerInvitation found = invitationRepository.findById(saved.getId()).orElseThrow();
        assertThat(found.getInvitationStatus()).isEqualTo(InvitationStatus.SENT);
        assertThat(found.getSentAt()).isNotNull();
    }

    @Test
    void should_incrementReminderCount_when_reminderSent() {
        // Given
        SpeakerInvitation invitation = createTestInvitation("reminder.test", "EVT-REM", generateUniqueToken());
        invitation.setInvitationStatus(InvitationStatus.SENT);
        SpeakerInvitation saved = invitationRepository.save(invitation);
        invitationRepository.flush();

        assertThat(saved.getReminderCount()).isZero();

        // When
        saved.setReminderCount(saved.getReminderCount() + 1);
        saved.setLastReminderAt(Instant.now());
        invitationRepository.save(saved);
        invitationRepository.flush();

        // Then
        SpeakerInvitation found = invitationRepository.findById(saved.getId()).orElseThrow();
        assertThat(found.getReminderCount()).isEqualTo(1);
        assertThat(found.getLastReminderAt()).isNotNull();
    }

    // AC9 Tests: Expiration

    @Test
    void should_storeExpirationDate_when_invitationCreated() {
        // Given
        Instant expiresAt = Instant.now().plus(14, ChronoUnit.DAYS);
        SpeakerInvitation invitation = SpeakerInvitation.builder()
                .username("expiry.test")
                .eventCode("EVT-EXP")
                .responseToken(generateUniqueToken())
                .expiresAt(expiresAt)
                .createdBy("organizer")
                .build();

        // When
        SpeakerInvitation saved = invitationRepository.save(invitation);
        invitationRepository.flush();

        // Then
        assertThat(saved.getExpiresAt()).isCloseTo(expiresAt, org.assertj.core.api.Assertions.within(1, ChronoUnit.SECONDS));
    }

    @Test
    void should_trackResponseDetails_when_speakerResponds() {
        // Given
        SpeakerInvitation invitation = createTestInvitation("respond.test", "EVT-RESP", generateUniqueToken());
        invitation.setInvitationStatus(InvitationStatus.SENT);
        SpeakerInvitation saved = invitationRepository.save(invitation);
        invitationRepository.flush();

        // When - Speaker accepts
        saved.setInvitationStatus(InvitationStatus.RESPONDED);
        saved.setResponseType(ResponseType.ACCEPTED);
        saved.setRespondedAt(Instant.now());
        invitationRepository.save(saved);
        invitationRepository.flush();

        // Then
        SpeakerInvitation found = invitationRepository.findById(saved.getId()).orElseThrow();
        assertThat(found.getInvitationStatus()).isEqualTo(InvitationStatus.RESPONDED);
        assertThat(found.getResponseType()).isEqualTo(ResponseType.ACCEPTED);
        assertThat(found.getRespondedAt()).isNotNull();
    }

    @Test
    void should_trackDeclineReason_when_speakerDeclines() {
        // Given
        SpeakerInvitation invitation = createTestInvitation("decline.test", "EVT-DEC", generateUniqueToken());
        invitation.setInvitationStatus(InvitationStatus.SENT);
        SpeakerInvitation saved = invitationRepository.save(invitation);
        invitationRepository.flush();

        // When - Speaker declines with reason
        saved.setInvitationStatus(InvitationStatus.RESPONDED);
        saved.setResponseType(ResponseType.DECLINED);
        saved.setDeclineReason("Schedule conflict with another event");
        saved.setRespondedAt(Instant.now());
        invitationRepository.save(saved);
        invitationRepository.flush();

        // Then
        SpeakerInvitation found = invitationRepository.findById(saved.getId()).orElseThrow();
        assertThat(found.getResponseType()).isEqualTo(ResponseType.DECLINED);
        assertThat(found.getDeclineReason()).isEqualTo("Schedule conflict with another event");
    }

    @Test
    void should_trackEmailMessageId_when_emailSent() {
        // Given
        SpeakerInvitation invitation = createTestInvitation("email.test", "EVT-EMAIL", generateUniqueToken());
        SpeakerInvitation saved = invitationRepository.save(invitation);

        // When
        saved.setEmailMessageId("aws-ses-message-id-12345");
        saved.setInvitationStatus(InvitationStatus.SENT);
        saved.setSentAt(Instant.now());
        invitationRepository.save(saved);
        invitationRepository.flush();

        // Then
        SpeakerInvitation found = invitationRepository.findById(saved.getId()).orElseThrow();
        assertThat(found.getEmailMessageId()).isEqualTo("aws-ses-message-id-12345");
    }

    @Test
    void should_useDefaultValues_when_minimalInvitationCreated() {
        // Given - Minimal required fields
        SpeakerInvitation invitation = SpeakerInvitation.builder()
                .username("minimal.speaker")
                .eventCode("EVT-MIN")
                .responseToken(generateUniqueToken())
                .expiresAt(Instant.now().plus(14, ChronoUnit.DAYS))
                .createdBy("organizer")
                .build();

        // When
        SpeakerInvitation saved = invitationRepository.save(invitation);
        invitationRepository.flush();

        // Then - verify defaults
        SpeakerInvitation found = invitationRepository.findById(saved.getId()).orElseThrow();
        assertThat(found.getInvitationStatus()).isEqualTo(InvitationStatus.PENDING);
        assertThat(found.getReminderCount()).isZero();
        assertThat(found.getSentAt()).isNull();
        assertThat(found.getRespondedAt()).isNull();
        assertThat(found.getResponseType()).isNull();
        assertThat(found.getDeclineReason()).isNull();
    }

    // Helper methods

    private SpeakerInvitation createTestInvitation(String username, String eventCode, String token) {
        return SpeakerInvitation.builder()
                .username(username)
                .eventCode(eventCode)
                .responseToken(token)
                .expiresAt(Instant.now().plus(14, ChronoUnit.DAYS))
                .createdBy("organizer.test")
                .build();
    }

    private String generateUniqueToken() {
        return java.util.UUID.randomUUID().toString().replace("-", "")
                + java.util.UUID.randomUUID().toString().replace("-", "");
    }
}
