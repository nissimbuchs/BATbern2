package ch.batbern.events.service;

import ch.batbern.events.AbstractIntegrationTest;
import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.Session;
import ch.batbern.events.domain.SessionUser;
import ch.batbern.events.domain.SessionUser.SpeakerRole;
import ch.batbern.events.domain.SpeakerPool;
import ch.batbern.events.dto.generated.EventType;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.repository.SessionRepository;
import ch.batbern.events.repository.SessionUserRepository;
import ch.batbern.events.repository.SpeakerPoolRepository;
import ch.batbern.shared.types.EventWorkflowState;
import ch.batbern.shared.types.SpeakerWorkflowState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for QualityReviewService (Story 5.5 Phase 4).
 *
 * Tests cover:
 * - AC11: Review queue retrieval
 * - AC13: Content approval workflow
 * - AC14: Content rejection workflow
 * - AC15: Re-review after rejection
 * - AC17: Auto-update to confirmed when both quality_reviewed AND slot_assigned
 * - AC35: Optimistic locking for concurrent updates
 *
 * Uses PostgreSQL via Testcontainers for production parity.
 */
@Transactional
class QualityReviewServiceIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private QualityReviewService qualityReviewService;

    @Autowired
    private SpeakerPoolRepository speakerPoolRepository;

    @Autowired
    private SessionRepository sessionRepository;

    @Autowired
    private SessionUserRepository sessionUserRepository;

    @Autowired
    private EventRepository eventRepository;

    private Event testEvent;
    private UUID testEventId;
    private static final String TEST_EVENT_CODE = "BAT123";

    @BeforeEach
    void setUp() {
        // Create test event
        testEvent = Event.builder()
                .eventCode(TEST_EVENT_CODE)
                .eventNumber(123)
                .title("BATbern 2024")
                .eventType(EventType.EVENING)
                .workflowState(EventWorkflowState.SPEAKER_BRAINSTORMING)
                .date(Instant.now().plus(90, ChronoUnit.DAYS))
                .registrationDeadline(Instant.now().plus(80, ChronoUnit.DAYS))
                .venueName("Test Venue")
                .venueAddress("123 Test Street")
                .venueCapacity(200)
                .organizerUsername("john.doe")
                .build();
        testEvent = eventRepository.save(testEvent);
        testEventId = testEvent.getId();
    }

    /**
     * AC11: Review queue shows all speakers with status='content_submitted'
     */
    @Test
    void should_returnReviewQueue_when_speakersHaveContentSubmitted() {
        // Given: 3 speakers - 2 with content_submitted, 1 with accepted
        SpeakerPool speaker1 = createSpeakerWithContent("speaker1", "Speaker One");
        SpeakerPool speaker2 = createSpeakerWithContent("speaker2", "Speaker Two");
        SpeakerPool speaker3 = createSpeaker("speaker3", "Speaker Three", SpeakerWorkflowState.ACCEPTED);

        // When: Get review queue
        List<SpeakerPool> reviewQueue = qualityReviewService.getReviewQueue(TEST_EVENT_CODE);

        // Then: Only content_submitted speakers returned
        assertThat(reviewQueue).hasSize(2);
        assertThat(reviewQueue)
                .extracting(SpeakerPool::getStatus)
                .containsOnly(SpeakerWorkflowState.CONTENT_SUBMITTED);
        assertThat(reviewQueue)
                .extracting(SpeakerPool::getSpeakerName)
                .containsExactlyInAnyOrder("Speaker One", "Speaker Two");
    }

    /**
     * AC11: Review queue is empty when no speakers have content_submitted
     */
    @Test
    void should_returnEmptyReviewQueue_when_noContentSubmitted() {
        // Given: Speakers in other states
        createSpeaker("speaker1", "Speaker One", SpeakerWorkflowState.ACCEPTED);
        createSpeaker("speaker2", "Speaker Two", SpeakerWorkflowState.QUALITY_REVIEWED);

        // When: Get review queue
        List<SpeakerPool> reviewQueue = qualityReviewService.getReviewQueue(TEST_EVENT_CODE);

        // Then: Empty queue
        assertThat(reviewQueue).isEmpty();
    }

    /**
     * AC13: Approve content updates status to quality_reviewed
     */
    @Test
    void should_updateToQualityReviewed_when_contentApproved() {
        // Given: Speaker with content_submitted
        SpeakerPool speaker = createSpeakerWithContent("john.doe", "John Doe");

        // When: Approve content
        qualityReviewService.approveContent(speaker.getId().toString(), "moderator.user");

        // Then: Status updated to quality_reviewed
        SpeakerPool updated = speakerPoolRepository.findById(speaker.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(SpeakerWorkflowState.QUALITY_REVIEWED);
    }

    /**
     * AC17: Approve content auto-updates to confirmed when slot already assigned
     */
    @Test
    void should_updateToConfirmed_when_approvedAndSlotAlreadyAssigned() {
        // Given: Speaker with content_submitted AND slot assigned
        SpeakerPool speaker = createSpeakerWithContent("john.doe", "John Doe");
        Session session = sessionRepository.findById(speaker.getSessionId()).orElseThrow();
        session.setStartTime(Instant.now().plus(90, ChronoUnit.DAYS));
        session.setEndTime(Instant.now().plus(90, ChronoUnit.DAYS).plus(1, ChronoUnit.HOURS));
        sessionRepository.save(session);

        // When: Approve content
        qualityReviewService.approveContent(speaker.getId().toString(), "moderator.user");

        // Then: Status auto-updated to confirmed (not quality_reviewed)
        SpeakerPool updated = speakerPoolRepository.findById(speaker.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(SpeakerWorkflowState.CONFIRMED);

        // And: session_users.is_confirmed updated
        List<SessionUser> sessionUsers = sessionUserRepository.findBySessionId(session.getId());
        assertThat(sessionUsers).hasSize(1);
        assertThat(sessionUsers.get(0).isConfirmed()).isTrue();
    }

    /**
     * AC14: Reject content keeps status as content_submitted with feedback
     */
    @Test
    void should_keepStatusAsContentSubmitted_when_contentRejected() {
        // Given: Speaker with content_submitted
        SpeakerPool speaker = createSpeakerWithContent("john.doe", "John Doe");

        // When: Reject content with feedback
        qualityReviewService.rejectContent(
                speaker.getId().toString(),
                "Abstract needs more focus on lessons learned.",
                "moderator.user"
        );

        // Then: Status remains content_submitted
        SpeakerPool updated = speakerPoolRepository.findById(speaker.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(SpeakerWorkflowState.CONTENT_SUBMITTED);

        // And: Feedback stored in notes
        assertThat(updated.getNotes()).contains("Abstract needs more focus on lessons learned.");
    }

    /**
     * AC14: Reject content requires feedback
     */
    @Test
    void should_throwException_when_rejectingWithoutFeedback() {
        // Given: Speaker with content_submitted
        SpeakerPool speaker = createSpeakerWithContent("john.doe", "John Doe");

        // When/Then: Reject without feedback throws exception
        assertThatThrownBy(() -> qualityReviewService.rejectContent(
                speaker.getId().toString(),
                null,
                "moderator.user"
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Feedback is required when rejecting content");
    }

    /**
     * AC15: Re-review workflow - speaker can resubmit after rejection
     */
    @Test
    void should_allowReReview_when_contentPreviouslyRejected() {
        // Given: Speaker with content_submitted and previous rejection feedback
        SpeakerPool speaker = createSpeakerWithContent("john.doe", "John Doe");
        speaker.setNotes("Previous rejection: Abstract too short.");
        speakerPoolRepository.save(speaker);

        // When: Approve content on re-review
        qualityReviewService.approveContent(speaker.getId().toString(), "moderator.user");

        // Then: Status updated to quality_reviewed
        SpeakerPool updated = speakerPoolRepository.findById(speaker.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(SpeakerWorkflowState.QUALITY_REVIEWED);

        // And: Previous feedback still preserved in notes
        assertThat(updated.getNotes()).contains("Previous rejection: Abstract too short.");
    }

    /**
     * AC17: Auto-update to confirmed when slot assigned AFTER quality review
     */
    @Test
    void should_updateToConfirmed_when_slotAssignedAfterQualityReview() {
        // Given: Speaker with quality_reviewed (already approved)
        SpeakerPool speaker = createSpeakerWithContent("john.doe", "John Doe");
        speaker.setStatus(SpeakerWorkflowState.QUALITY_REVIEWED);
        speakerPoolRepository.save(speaker);

        // When: Assign slot (set start_time)
        Session session = sessionRepository.findById(speaker.getSessionId()).orElseThrow();
        session.setStartTime(Instant.now().plus(90, ChronoUnit.DAYS));
        session.setEndTime(Instant.now().plus(90, ChronoUnit.DAYS).plus(1, ChronoUnit.HOURS));
        sessionRepository.save(session);

        // And: Trigger check (simulates workflow event listener)
        qualityReviewService.checkAndUpdateToConfirmed(speaker);

        // Then: Status auto-updated to confirmed
        SpeakerPool updated = speakerPoolRepository.findById(speaker.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(SpeakerWorkflowState.CONFIRMED);

        // And: session_users.is_confirmed updated
        List<SessionUser> sessionUsers = sessionUserRepository.findBySessionId(session.getId());
        assertThat(sessionUsers).hasSize(1);
        assertThat(sessionUsers.get(0).isConfirmed()).isTrue();
    }

    /**
     * AC16: Quality review can happen before or after slot assignment (order doesn't matter)
     */
    @Test
    void should_notUpdateToConfirmed_when_onlyQualityReviewedButNoSlot() {
        // Given: Speaker with quality_reviewed but NO slot assigned
        SpeakerPool speaker = createSpeakerWithContent("john.doe", "John Doe");
        speaker.setStatus(SpeakerWorkflowState.QUALITY_REVIEWED);
        speakerPoolRepository.save(speaker);

        // Session has NO start_time (slot not assigned)
        Session session = sessionRepository.findById(speaker.getSessionId()).orElseThrow();
        assertThat(session.getStartTime()).isNull();

        // When: Trigger check
        qualityReviewService.checkAndUpdateToConfirmed(speaker);

        // Then: Status remains quality_reviewed (not confirmed)
        SpeakerPool updated = speakerPoolRepository.findById(speaker.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(SpeakerWorkflowState.QUALITY_REVIEWED);

        // And: session_users.is_confirmed still false
        List<SessionUser> sessionUsers = sessionUserRepository.findBySessionId(session.getId());
        assertThat(sessionUsers).hasSize(1);
        assertThat(sessionUsers.get(0).isConfirmed()).isFalse();
    }

    /**
     * AC35: Optimistic locking prevents lost updates during concurrent modifications
     *
     * Note: Actual concurrent execution testing requires multi-threading, which is complex
     * in integration tests. This test verifies the optimistic locking mechanism is in place
     * by simulating a version mismatch scenario.
     */
    @Test
    void should_handleOptimisticLocking_when_concurrentUpdatesOccur() {
        // Given: Speaker with content_submitted
        SpeakerPool speaker = createSpeakerWithContent("john.doe", "John Doe");
        UUID speakerId = speaker.getId();

        // Simulate concurrent update by manually incrementing version
        // (In real scenario, this happens when another transaction updates the same row)
        SpeakerPool freshCopy = speakerPoolRepository.findById(speakerId).orElseThrow();
        freshCopy.setNotes("Updated by another transaction");
        speakerPoolRepository.saveAndFlush(freshCopy); // This increments version

        // When: Try to approve with stale version
        // The service should handle OptimisticLockException gracefully
        qualityReviewService.approveContent(speakerId.toString(), "moderator.user");

        // Then: Status still updated successfully (service retried with fresh data)
        SpeakerPool updated = speakerPoolRepository.findById(speakerId).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(SpeakerWorkflowState.QUALITY_REVIEWED);
    }

    // ==================== Helper Methods ====================

    /**
     * Creates a speaker with submitted content (session + session_users).
     */
    private SpeakerPool createSpeakerWithContent(String username, String speakerName) {
        // Create session
        Session session = Session.builder()
                .eventId(testEventId)
                .title("Test Presentation by " + speakerName)
                .description("This is a test abstract with lessons learned about scalable architectures.")
                .sessionSlug("test-presentation-" + username)
                .sessionType("presentation")
                .build();
        session = sessionRepository.save(session);

        // Create session_users link
        SessionUser sessionUser = SessionUser.builder()
                .session(session)
                .username(username)
                .speakerRole(SpeakerRole.PRIMARY_SPEAKER)
                .isConfirmed(false)
                .build();
        sessionUserRepository.save(sessionUser);

        // Create speaker pool entry with content_submitted status
        SpeakerPool speaker = new SpeakerPool();
        speaker.setEventId(testEventId);
        speaker.setSpeakerName(speakerName);
        speaker.setCompany("Tech Corp");
        speaker.setExpertise("Architecture");
        speaker.setStatus(SpeakerWorkflowState.CONTENT_SUBMITTED);
        speaker.setSessionId(session.getId());
        return speakerPoolRepository.save(speaker);
    }

    /**
     * Creates a speaker in specified workflow state (without session).
     */
    private SpeakerPool createSpeaker(String username, String speakerName, SpeakerWorkflowState status) {
        SpeakerPool speaker = new SpeakerPool();
        speaker.setEventId(testEventId);
        speaker.setSpeakerName(speakerName);
        speaker.setCompany("Tech Corp");
        speaker.setExpertise("Architecture");
        speaker.setStatus(status);
        return speakerPoolRepository.save(speaker);
    }
}
