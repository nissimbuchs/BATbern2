package ch.batbern.events.service;

import ch.batbern.events.AbstractIntegrationTest;
import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.Session;
import ch.batbern.events.domain.SessionUser;
import ch.batbern.events.domain.SpeakerPool;
import ch.batbern.events.dto.SpeakerContentResponse;
import ch.batbern.events.dto.SubmitContentRequest;
import ch.batbern.events.dto.generated.EventType;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.repository.SessionRepository;
import ch.batbern.events.repository.SessionUserRepository;
import ch.batbern.events.repository.SpeakerPoolRepository;
import ch.batbern.shared.events.SpeakerWorkflowStateChangeEvent;
import ch.batbern.shared.types.EventWorkflowState;
import ch.batbern.shared.types.SpeakerWorkflowState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for SpeakerContentSubmissionService (Story 5.5 Phase 3).
 *
 * Tests the complete content submission workflow including:
 * - Session creation (AC7)
 * - Session-speaker link via session_users (AC8)
 * - Speaker pool status update (AC10)
 * - Domain event publication (AC10)
 * - Error handling (AC28-37)
 *
 * Uses PostgreSQL via Testcontainers for production parity.
 */
@Transactional
@RecordApplicationEvents
class SpeakerContentSubmissionServiceIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private SpeakerContentSubmissionService contentSubmissionService;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private SpeakerPoolRepository speakerPoolRepository;

    @Autowired
    private SessionRepository sessionRepository;

    @Autowired
    private SessionUserRepository sessionUserRepository;

    @Autowired
    private ApplicationEvents applicationEvents;

    private UUID testEventId;
    private UUID testSpeakerPoolId;

    @BeforeEach
    void setUp() {
        // Create test event (required for FK constraint)
        Event event = Event.builder()
                .eventCode("TEST-EVENT-01")
                .eventNumber(123)
                .eventType(EventType.FULL_DAY)
                .date(Instant.now().plusSeconds(90 * 24 * 60 * 60)) // 90 days from now
                .title("Test Event")
                .venueName("Test Venue")
                .venueAddress("Test Address")
                .venueCapacity(100)
                .registrationDeadline(Instant.now().plusSeconds(60 * 24 * 60 * 60)) // 60 days from now
                .organizerUsername("test-organizer")
                .workflowState(EventWorkflowState.TOPIC_SELECTION)
                .build();
        event = eventRepository.save(event);
        testEventId = event.getId();

        // Create an accepted speaker in the pool (AC6 precondition)
        SpeakerPool speaker = new SpeakerPool();
        speaker.setEventId(testEventId);
        speaker.setSpeakerName("John Doe");
        speaker.setCompany("Tech Corp");
        speaker.setExpertise("Cloud Architecture");
        speaker.setStatus(SpeakerWorkflowState.ACCEPTED);
        speaker = speakerPoolRepository.save(speaker);
        testSpeakerPoolId = speaker.getId();
    }

    /**
     * AC7: Session Creation
     * AC8: Session-Speaker Link
     * AC10: Workflow State Update
     */
    @Test
    void should_createSessionAndLinkSpeaker_when_contentSubmitted() {
        // Given: Accepted speaker and valid content
        SubmitContentRequest request = SubmitContentRequest.builder()
                .presentationTitle("Building Scalable Microservices")
                .presentationAbstract("In this presentation, I'll share lessons learned from building scalable microservices architectures in production.")
                .username("john.doe")
                .build();

        // When: Content is submitted
        SpeakerContentResponse response = contentSubmissionService.submitContent(
                testSpeakerPoolId.toString(),
                "TEST-EVENT-01",
                request.getPresentationTitle(),
                request.getPresentationAbstract(),
                request.getUsername(),
                null, // speakerName (not needed when username provided)
                null, // email (not needed when username provided)
                null  // company (not needed when username provided)
        );

        // Then: Session is created (AC7)
        assertThat(response.getSessionId()).isNotNull();
        Session session = sessionRepository.findById(response.getSessionId()).orElseThrow();
        assertThat(session.getTitle()).isEqualTo("Building Scalable Microservices");
        assertThat(session.getDescription()).isEqualTo("In this presentation, I'll share lessons learned from building scalable microservices architectures in production.");
        assertThat(session.getSessionType()).isEqualTo("presentation");
        assertThat(session.getEventId()).isEqualTo(testEventId);

        // And: Session-speaker link is created (AC8)
        List<SessionUser> sessionUsers = sessionUserRepository.findBySessionId(session.getId());
        assertThat(sessionUsers).hasSize(1);
        SessionUser sessionUser = sessionUsers.get(0);
        assertThat(sessionUser.getUsername()).isEqualTo("john.doe");
        assertThat(sessionUser.getSpeakerRole()).isEqualTo(SessionUser.SpeakerRole.PRIMARY_SPEAKER);
        assertThat(sessionUser.isConfirmed()).isFalse(); // AC8: not confirmed until both quality_reviewed AND slot_assigned

        // And: Speaker pool status is updated (AC10)
        SpeakerPool updatedSpeaker = speakerPoolRepository.findById(testSpeakerPoolId).orElseThrow();
        assertThat(updatedSpeaker.getStatus()).isEqualTo(SpeakerWorkflowState.CONTENT_SUBMITTED);
        assertThat(updatedSpeaker.getSessionId()).isEqualTo(session.getId());

        // And: Domain event is published (AC10)
        long eventCount = applicationEvents.stream(SpeakerWorkflowStateChangeEvent.class).count();
        assertThat(eventCount).isEqualTo(1);

        SpeakerWorkflowStateChangeEvent event = applicationEvents.stream(SpeakerWorkflowStateChangeEvent.class)
                .findFirst()
                .orElseThrow();
        assertThat(event.getSpeakerPoolId()).isEqualTo(testSpeakerPoolId);
        assertThat(event.getRelatedEventId()).isEqualTo(testEventId);
        assertThat(event.getFromState()).isEqualTo(SpeakerWorkflowState.ACCEPTED);
        assertThat(event.getToState()).isEqualTo(SpeakerWorkflowState.CONTENT_SUBMITTED);
        assertThat(event.getUsername()).isEqualTo("john.doe");
    }

    /**
     * AC37: Content Submission State Validation
     */
    @Test
    void should_rejectSubmission_when_speakerNotInAcceptedState() {
        // Given: Speaker in IDENTIFIED state (not ACCEPTED)
        SpeakerPool speaker = speakerPoolRepository.findById(testSpeakerPoolId).orElseThrow();
        speaker.setStatus(SpeakerWorkflowState.IDENTIFIED);
        speakerPoolRepository.save(speaker);

        // When/Then: Content submission is rejected
        assertThatThrownBy(() -> contentSubmissionService.submitContent(
                testSpeakerPoolId.toString(),
                "TEST-EVENT-01",
                "Test Title",
                "Test abstract with lessons learned.",
                "john.doe",
                null, null, null
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Speaker must be accepted before content submission");
    }

    /**
     * AC6: Validation - missing required fields
     */
    @Test
    void should_rejectSubmission_when_titleMissing() {
        // When/Then: Submission with null title is rejected
        assertThatThrownBy(() -> contentSubmissionService.submitContent(
                testSpeakerPoolId.toString(),
                "TEST-EVENT-01",
                null, // missing title
                "Test abstract",
                "john.doe",
                null, null, null
        ))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * AC6: Validation - missing required fields
     */
    @Test
    void should_rejectSubmission_when_abstractMissing() {
        // When/Then: Submission with null abstract is rejected
        assertThatThrownBy(() -> contentSubmissionService.submitContent(
                testSpeakerPoolId.toString(),
                "TEST-EVENT-01",
                "Test Title",
                null, // missing abstract
                "john.doe",
                null, null, null
        ))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * AC33: Transaction Integrity
     * Ensures rollback when any part of content submission fails
     */
    @Test
    void should_rollbackTransaction_when_anyDatabaseWriteFails() {
        // Given: Invalid speaker pool ID (will cause EntityNotFoundException)
        UUID invalidPoolId = UUID.randomUUID();

        // When/Then: Transaction fails and rolls back
        assertThatThrownBy(() -> contentSubmissionService.submitContent(
                invalidPoolId.toString(),
                "TEST-EVENT-01",
                "Test Title",
                "Test abstract",
                "john.doe",
                null, null, null
        ))
                .isInstanceOf(Exception.class);

        // And: No orphaned session records exist
        List<Session> sessions = sessionRepository.findAll();
        assertThat(sessions).isEmpty();

        // And: No orphaned session_users records exist
        List<SessionUser> sessionUsers = sessionUserRepository.findAll();
        assertThat(sessionUsers).isEmpty();

        // And: Speaker pool status remains unchanged
        SpeakerPool speaker = speakerPoolRepository.findById(testSpeakerPoolId).orElseThrow();
        assertThat(speaker.getStatus()).isEqualTo(SpeakerWorkflowState.ACCEPTED);
        assertThat(speaker.getSessionId()).isNull();
    }

    /**
     * AC34: Session Deletion Protection (Orphaned FK Handling)
     * Tests getSpeakerContent() method's resilience to orphaned session references
     */
    @Test
    void should_handleOrphanedSessionReference_when_sessionDeleted() {
        // Given: Speaker with submitted content
        SubmitContentRequest request = SubmitContentRequest.builder()
                .presentationTitle("Test Title")
                .presentationAbstract("Test abstract")
                .username("john.doe")
                .build();

        SpeakerContentResponse submitted = contentSubmissionService.submitContent(
                testSpeakerPoolId.toString(),
                "TEST-EVENT-01",
                request.getPresentationTitle(),
                request.getPresentationAbstract(),
                request.getUsername(),
                null, null, null
        );

        UUID sessionId = submitted.getSessionId();

        // When: Session is deleted (simulating accidental deletion)
        sessionRepository.deleteById(sessionId);

        // Then: getSpeakerContent() detects orphaned FK and recovers (AC34)
        SpeakerContentResponse content = contentSubmissionService.getSpeakerContent(testSpeakerPoolId.toString());

        assertThat(content.isHasContent()).isFalse();
        assertThat(content.getWarning()).isEqualTo("Content was lost. Please resubmit.");

        // And: Speaker status is reset to ACCEPTED
        SpeakerPool speaker = speakerPoolRepository.findById(testSpeakerPoolId).orElseThrow();
        assertThat(speaker.getStatus()).isEqualTo(SpeakerWorkflowState.ACCEPTED);
        assertThat(speaker.getSessionId()).isNull();
    }
}
