package ch.batbern.events.service;

import ch.batbern.shared.test.AbstractIntegrationTest;
import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.Session;
import ch.batbern.events.domain.SpeakerPool;
import ch.batbern.events.dto.generated.EventType;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.repository.SessionRepository;
import ch.batbern.events.repository.SpeakerPoolRepository;
import ch.batbern.shared.types.EventWorkflowState;
import ch.batbern.shared.types.SpeakerWorkflowState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for SpeakerWorkflowService (Story 6.0a).
 *
 * Tests cover:
 * - Valid state transitions (linear workflow)
 * - Invalid state transitions (rejection)
 * - Idempotent operations (same state to same state)
 * - Terminal states (DECLINED, CONFIRMED)
 * - Domain event publishing
 * - Overflow detection
 *
 * Uses PostgreSQL via Testcontainers for production parity.
 */
@Transactional
class SpeakerWorkflowServiceIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private SpeakerWorkflowService workflowService;

    @Autowired
    private SpeakerPoolRepository speakerPoolRepository;

    @Autowired
    private SessionRepository sessionRepository;

    @Autowired
    private EventRepository eventRepository;

    private Event testEvent;
    private UUID testEventId;
    private static final String TEST_EVENT_CODE = "BAT-WORKFLOW-TEST";
    private static final String ORGANIZER_USERNAME = "john.doe";

    @BeforeEach
    void setUp() {
        // Create test event
        testEvent = Event.builder()
                .eventCode(TEST_EVENT_CODE)
                .eventNumber(999)
                .title("BATbern Workflow Test")
                .eventType(EventType.EVENING)
                .workflowState(EventWorkflowState.SPEAKER_IDENTIFICATION)
                .date(Instant.now().plus(90, ChronoUnit.DAYS))
                .registrationDeadline(Instant.now().plus(80, ChronoUnit.DAYS))
                .venueName("Test Venue")
                .venueAddress("123 Test Street")
                .venueCapacity(200)
                .organizerUsername(ORGANIZER_USERNAME)
                .build();
        testEvent = eventRepository.save(testEvent);
        testEventId = testEvent.getId();
    }

    @Nested
    @DisplayName("Valid State Transitions")
    class ValidTransitions {

        @Test
        @DisplayName("should transition IDENTIFIED -> CONTACTED")
        void should_transitionIdentifiedToContacted() {
            // Given: Speaker in IDENTIFIED state
            SpeakerPool speaker = createSpeaker(SpeakerWorkflowState.IDENTIFIED);

            // When: Transition to CONTACTED
            workflowService.updateSpeakerWorkflowState(
                    speaker.getId(),
                    SpeakerWorkflowState.CONTACTED,
                    ORGANIZER_USERNAME
            );

            // Then: Status updated to CONTACTED
            SpeakerPool updated = speakerPoolRepository.findById(speaker.getId()).orElseThrow();
            assertThat(updated.getStatus()).isEqualTo(SpeakerWorkflowState.CONTACTED);
        }

        @Test
        @DisplayName("should transition CONTACTED -> READY")
        void should_transitionContactedToReady() {
            // Given: Speaker in CONTACTED state
            SpeakerPool speaker = createSpeaker(SpeakerWorkflowState.CONTACTED);

            // When: Transition to READY
            workflowService.updateSpeakerWorkflowState(
                    speaker.getId(),
                    SpeakerWorkflowState.READY,
                    ORGANIZER_USERNAME
            );

            // Then: Status updated to READY
            SpeakerPool updated = speakerPoolRepository.findById(speaker.getId()).orElseThrow();
            assertThat(updated.getStatus()).isEqualTo(SpeakerWorkflowState.READY);
        }

        @Test
        @DisplayName("should transition READY -> ACCEPTED")
        void should_transitionReadyToAccepted() {
            // Given: Speaker in READY state
            SpeakerPool speaker = createSpeaker(SpeakerWorkflowState.READY);

            // When: Transition to ACCEPTED
            workflowService.updateSpeakerWorkflowState(
                    speaker.getId(),
                    SpeakerWorkflowState.ACCEPTED,
                    ORGANIZER_USERNAME
            );

            // Then: Status updated to ACCEPTED
            SpeakerPool updated = speakerPoolRepository.findById(speaker.getId()).orElseThrow();
            assertThat(updated.getStatus()).isEqualTo(SpeakerWorkflowState.ACCEPTED);
        }

        @Test
        @DisplayName("should transition through full workflow: IDENTIFIED -> ... -> QUALITY_REVIEWED")
        void should_transitionThroughFullWorkflow() {
            // Given: Speaker in IDENTIFIED state
            SpeakerPool speaker = createSpeaker(SpeakerWorkflowState.IDENTIFIED);

            // When: Transition through full workflow
            workflowService.updateSpeakerWorkflowState(speaker.getId(), SpeakerWorkflowState.CONTACTED, ORGANIZER_USERNAME);
            workflowService.updateSpeakerWorkflowState(speaker.getId(), SpeakerWorkflowState.READY, ORGANIZER_USERNAME);
            workflowService.updateSpeakerWorkflowState(speaker.getId(), SpeakerWorkflowState.ACCEPTED, ORGANIZER_USERNAME);
            workflowService.updateSpeakerWorkflowState(speaker.getId(), SpeakerWorkflowState.CONTENT_SUBMITTED, ORGANIZER_USERNAME);
            workflowService.updateSpeakerWorkflowState(speaker.getId(), SpeakerWorkflowState.QUALITY_REVIEWED, ORGANIZER_USERNAME);

            // Then: Status updated to QUALITY_REVIEWED (CONFIRMED requires slot assignment)
            SpeakerPool updated = speakerPoolRepository.findById(speaker.getId()).orElseThrow();
            assertThat(updated.getStatus()).isEqualTo(SpeakerWorkflowState.QUALITY_REVIEWED);
        }

        @Test
        @DisplayName("should auto-confirm when QUALITY_REVIEWED and slot assigned")
        void should_autoConfirmWhenQualityReviewedAndSlotAssigned() {
            // Given: Speaker in CONTENT_SUBMITTED with session having start_time
            SpeakerPool speaker = createSpeakerWithSession(SpeakerWorkflowState.CONTENT_SUBMITTED);

            // When: Transition to QUALITY_REVIEWED
            workflowService.updateSpeakerWorkflowState(
                    speaker.getId(),
                    SpeakerWorkflowState.QUALITY_REVIEWED,
                    ORGANIZER_USERNAME
            );

            // Then: Status auto-updated to CONFIRMED
            SpeakerPool updated = speakerPoolRepository.findById(speaker.getId()).orElseThrow();
            assertThat(updated.getStatus()).isEqualTo(SpeakerWorkflowState.CONFIRMED);
        }
    }

    @Nested
    @DisplayName("Invalid State Transitions")
    class InvalidTransitions {

        @Test
        @DisplayName("should reject IDENTIFIED -> ACCEPTED (skipping steps)")
        void should_rejectSkippingSteps() {
            // Given: Speaker in IDENTIFIED state
            SpeakerPool speaker = createSpeaker(SpeakerWorkflowState.IDENTIFIED);

            // When/Then: Transition directly to ACCEPTED should throw
            assertThatThrownBy(() -> workflowService.updateSpeakerWorkflowState(
                    speaker.getId(),
                    SpeakerWorkflowState.ACCEPTED,
                    ORGANIZER_USERNAME
            )).isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Invalid state transition");
        }

        @Test
        @DisplayName("should reject transition from DECLINED (terminal state)")
        void should_rejectTransitionFromDeclined() {
            // Given: Speaker in DECLINED state (terminal)
            SpeakerPool speaker = createSpeaker(SpeakerWorkflowState.DECLINED);

            // When/Then: Any transition from DECLINED should throw
            assertThatThrownBy(() -> workflowService.updateSpeakerWorkflowState(
                    speaker.getId(),
                    SpeakerWorkflowState.CONTACTED,
                    ORGANIZER_USERNAME
            )).isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Invalid state transition");
        }

        @Test
        @DisplayName("should reject transition from CONFIRMED (terminal state)")
        void should_rejectTransitionFromConfirmed() {
            // Given: Speaker in CONFIRMED state (terminal)
            SpeakerPool speaker = createSpeaker(SpeakerWorkflowState.CONFIRMED);

            // When/Then: Any transition from CONFIRMED should throw
            assertThatThrownBy(() -> workflowService.updateSpeakerWorkflowState(
                    speaker.getId(),
                    SpeakerWorkflowState.ACCEPTED,
                    ORGANIZER_USERNAME
            )).isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Invalid state transition");
        }

        @Test
        @DisplayName("should reject ACCEPTED -> CONTACTED (backwards transition)")
        void should_rejectBackwardsTransition() {
            // Given: Speaker in ACCEPTED state
            SpeakerPool speaker = createSpeaker(SpeakerWorkflowState.ACCEPTED);

            // When/Then: Transition backwards to CONTACTED should throw
            assertThatThrownBy(() -> workflowService.updateSpeakerWorkflowState(
                    speaker.getId(),
                    SpeakerWorkflowState.CONTACTED,
                    ORGANIZER_USERNAME
            )).isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Invalid state transition");
        }
    }

    @Nested
    @DisplayName("Idempotent Operations")
    class IdempotentOperations {

        @Test
        @DisplayName("should allow CONTACTED -> CONTACTED (idempotent)")
        void should_allowIdempotentTransition() {
            // Given: Speaker in CONTACTED state
            SpeakerPool speaker = createSpeaker(SpeakerWorkflowState.CONTACTED);

            // When: Transition to same state
            workflowService.updateSpeakerWorkflowState(
                    speaker.getId(),
                    SpeakerWorkflowState.CONTACTED,
                    ORGANIZER_USERNAME
            );

            // Then: Should succeed without exception
            SpeakerPool updated = speakerPoolRepository.findById(speaker.getId()).orElseThrow();
            assertThat(updated.getStatus()).isEqualTo(SpeakerWorkflowState.CONTACTED);
        }
    }

    @Nested
    @DisplayName("Alternative Flows")
    class AlternativeFlows {

        @Test
        @DisplayName("should allow IDENTIFIED -> DECLINED")
        void should_allowDeclineFromIdentified() {
            // Given: Speaker in IDENTIFIED state
            SpeakerPool speaker = createSpeaker(SpeakerWorkflowState.IDENTIFIED);

            // When: Transition to DECLINED
            workflowService.updateSpeakerWorkflowState(
                    speaker.getId(),
                    SpeakerWorkflowState.DECLINED,
                    ORGANIZER_USERNAME
            );

            // Then: Status updated to DECLINED
            SpeakerPool updated = speakerPoolRepository.findById(speaker.getId()).orElseThrow();
            assertThat(updated.getStatus()).isEqualTo(SpeakerWorkflowState.DECLINED);
        }

        @Test
        @DisplayName("should allow ACCEPTED -> WITHDREW")
        void should_allowWithdrewFromAccepted() {
            // Given: Speaker in ACCEPTED state
            SpeakerPool speaker = createSpeaker(SpeakerWorkflowState.ACCEPTED);

            // When: Transition to WITHDREW
            workflowService.updateSpeakerWorkflowState(
                    speaker.getId(),
                    SpeakerWorkflowState.WITHDREW,
                    ORGANIZER_USERNAME
            );

            // Then: Status updated to WITHDREW
            SpeakerPool updated = speakerPoolRepository.findById(speaker.getId()).orElseThrow();
            assertThat(updated.getStatus()).isEqualTo(SpeakerWorkflowState.WITHDREW);
        }

        @Test
        @DisplayName("should allow WITHDREW -> ACCEPTED (re-acceptance)")
        void should_allowReAcceptanceFromWithdrew() {
            // Given: Speaker in WITHDREW state
            SpeakerPool speaker = createSpeaker(SpeakerWorkflowState.WITHDREW);

            // When: Transition back to ACCEPTED
            workflowService.updateSpeakerWorkflowState(
                    speaker.getId(),
                    SpeakerWorkflowState.ACCEPTED,
                    ORGANIZER_USERNAME
            );

            // Then: Status updated to ACCEPTED
            SpeakerPool updated = speakerPoolRepository.findById(speaker.getId()).orElseThrow();
            assertThat(updated.getStatus()).isEqualTo(SpeakerWorkflowState.ACCEPTED);
        }

        @Test
        @DisplayName("should allow ACCEPTED -> OVERFLOW")
        void should_allowOverflowFromAccepted() {
            // Given: Speaker in ACCEPTED state
            SpeakerPool speaker = createSpeaker(SpeakerWorkflowState.ACCEPTED);

            // When: Transition to OVERFLOW
            workflowService.updateSpeakerWorkflowState(
                    speaker.getId(),
                    SpeakerWorkflowState.OVERFLOW,
                    ORGANIZER_USERNAME
            );

            // Then: Status updated to OVERFLOW
            SpeakerPool updated = speakerPoolRepository.findById(speaker.getId()).orElseThrow();
            assertThat(updated.getStatus()).isEqualTo(SpeakerWorkflowState.OVERFLOW);
        }

        @Test
        @DisplayName("should allow OVERFLOW -> ACCEPTED (slot opened up)")
        void should_allowAcceptedFromOverflow() {
            // Given: Speaker in OVERFLOW state
            SpeakerPool speaker = createSpeaker(SpeakerWorkflowState.OVERFLOW);

            // When: Transition to ACCEPTED
            workflowService.updateSpeakerWorkflowState(
                    speaker.getId(),
                    SpeakerWorkflowState.ACCEPTED,
                    ORGANIZER_USERNAME
            );

            // Then: Status updated to ACCEPTED
            SpeakerPool updated = speakerPoolRepository.findById(speaker.getId()).orElseThrow();
            assertThat(updated.getStatus()).isEqualTo(SpeakerWorkflowState.ACCEPTED);
        }
    }

    @Nested
    @DisplayName("Overflow Detection")
    class OverflowDetection {

        @Test
        @DisplayName("should detect overflow when accepted count exceeds max slots")
        void should_detectOverflow() {
            // Given: Many accepted speakers (venue capacity 200 / 25 = 8 max slots)
            for (int i = 0; i < 10; i++) {
                createSpeaker(SpeakerWorkflowState.ACCEPTED);
            }

            // When: Check for overflow
            boolean isOverflow = workflowService.checkForOverflow(testEventId);

            // Then: Overflow detected (10 > 8)
            assertThat(isOverflow).isTrue();
        }

        @Test
        @DisplayName("should not detect overflow when accepted count within max slots")
        void should_notDetectOverflowWhenWithinLimit() {
            // Given: Few accepted speakers
            for (int i = 0; i < 3; i++) {
                createSpeaker(SpeakerWorkflowState.ACCEPTED);
            }

            // When: Check for overflow
            boolean isOverflow = workflowService.checkForOverflow(testEventId);

            // Then: No overflow (3 < 8)
            assertThat(isOverflow).isFalse();
        }

        @Test
        @DisplayName("should count CONFIRMED speakers in overflow check")
        void should_countConfirmedInOverflow() {
            // Given: Mix of accepted and confirmed speakers
            for (int i = 0; i < 5; i++) {
                createSpeaker(SpeakerWorkflowState.ACCEPTED);
            }
            for (int i = 0; i < 5; i++) {
                createSpeaker(SpeakerWorkflowState.CONFIRMED);
            }

            // When: Check for overflow
            boolean isOverflow = workflowService.checkForOverflow(testEventId);

            // Then: Overflow detected (5 + 5 = 10 > 8)
            assertThat(isOverflow).isTrue();
        }
    }

    @Nested
    @DisplayName("Error Handling")
    class ErrorHandling {

        @Test
        @DisplayName("should throw when speaker not found")
        void should_throwWhenSpeakerNotFound() {
            // Given: Non-existent speaker ID
            UUID nonExistentId = UUID.randomUUID();

            // When/Then: Should throw IllegalArgumentException
            assertThatThrownBy(() -> workflowService.updateSpeakerWorkflowState(
                    nonExistentId,
                    SpeakerWorkflowState.CONTACTED,
                    ORGANIZER_USERNAME
            )).isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Speaker not found");
        }

        @Test
        @DisplayName("should throw when event not found for overflow check")
        void should_throwWhenEventNotFoundForOverflow() {
            // Given: Non-existent event ID
            UUID nonExistentEventId = UUID.randomUUID();

            // When/Then: Should throw IllegalArgumentException
            assertThatThrownBy(() -> workflowService.checkForOverflow(nonExistentEventId))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Event not found");
        }
    }

    // ==================== Helper Methods ====================

    /**
     * Creates a speaker in specified workflow state.
     */
    private SpeakerPool createSpeaker(SpeakerWorkflowState status) {
        SpeakerPool speaker = new SpeakerPool();
        speaker.setEventId(testEventId);
        speaker.setSpeakerName("Test Speaker " + UUID.randomUUID().toString().substring(0, 8));
        speaker.setCompany("Tech Corp");
        speaker.setExpertise("Architecture");
        speaker.setStatus(status);
        return speakerPoolRepository.save(speaker);
    }

    /**
     * Creates a speaker with an associated session that has start_time set (slot assigned).
     */
    private SpeakerPool createSpeakerWithSession(SpeakerWorkflowState status) {
        // Create session with start_time
        Session session = Session.builder()
                .eventId(testEventId)
                .eventCode(testEvent.getEventCode())
                .title("Test Presentation")
                .description("Test abstract")
                .sessionSlug("test-presentation-" + UUID.randomUUID().toString().substring(0, 8))
                .sessionType("presentation")
                .startTime(Instant.now().plus(90, ChronoUnit.DAYS))
                .endTime(Instant.now().plus(90, ChronoUnit.DAYS).plus(1, ChronoUnit.HOURS))
                .build();
        session = sessionRepository.save(session);

        // Create speaker with session link
        SpeakerPool speaker = new SpeakerPool();
        speaker.setEventId(testEventId);
        speaker.setSpeakerName("Test Speaker with Session");
        speaker.setCompany("Tech Corp");
        speaker.setExpertise("Architecture");
        speaker.setStatus(status);
        speaker.setSessionId(session.getId());
        return speakerPoolRepository.save(speaker);
    }
}
