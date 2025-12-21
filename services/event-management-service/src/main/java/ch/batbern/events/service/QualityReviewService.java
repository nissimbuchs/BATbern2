package ch.batbern.events.service;

import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.Session;
import ch.batbern.events.domain.SpeakerPool;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.repository.SessionRepository;
import ch.batbern.events.repository.SessionUserRepository;
import ch.batbern.events.repository.SpeakerPoolRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Service for quality review workflow (Story 5.5 AC11-15).
 *
 * Handles:
 * - Review queue for speakers with status='content_submitted'
 * - Content approval (quality_reviewed) with timestamp
 * - Content rejection with feedback (status remains content_submitted)
 * - Re-review workflow after rejection
 * - Automatic update to 'confirmed' when both quality_reviewed AND slot_assigned (AC16-17)
 *
 * Quality Review Criteria (AC12):
 * - Abstract length <= 1000 characters
 * - "Lessons learned" detected (auto-flag if missing)
 * - No product promotion detected (auto-flag if found)
 * - Professional tone check
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QualityReviewService {

    private final EventRepository eventRepository;
    private final SpeakerPoolRepository speakerPoolRepository;
    private final SessionRepository sessionRepository;
    private final SessionUserRepository sessionUserRepository;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * Get review queue for an event.
     *
     * Returns all speakers with status='content_submitted', sorted by submission date (oldest first).
     *
     * @param eventCode the event code
     * @return list of speakers pending review (AC11)
     */
    public List<SpeakerPool> getReviewQueue(String eventCode) {
        log.debug("Fetching review queue for event: {}", eventCode);

        // Convert event code to event ID
        Event event = eventRepository.findByEventCode(eventCode)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("Event not found: " + eventCode));

        return speakerPoolRepository.findByEventIdAndStatusOrderByCreatedAtAsc(
                event.getId(),
                ch.batbern.shared.types.SpeakerWorkflowState.CONTENT_SUBMITTED
        );
    }

    /**
     * Approve speaker content.
     *
     * Updates status to 'quality_reviewed' and checks if speaker should auto-update to 'confirmed'
     * (if slot also assigned). Uses optimistic locking to handle concurrent updates (AC35).
     *
     * @param poolId the speaker pool ID
     * @param moderatorUsername the moderator approving the content
     * @throws jakarta.persistence.OptimisticLockException if concurrent update detected
     */
    @Transactional
    public void approveContent(String poolId, String moderatorUsername) {
        log.info("Approving content for speaker pool entry: {} by moderator: {}", poolId, moderatorUsername);

        SpeakerPool speaker = speakerPoolRepository.findById(java.util.UUID.fromString(poolId))
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException(
                        "Speaker pool entry not found: " + poolId));

        ch.batbern.shared.types.SpeakerWorkflowState previousState = speaker.getStatus();
        speaker.setStatus(ch.batbern.shared.types.SpeakerWorkflowState.QUALITY_REVIEWED);
        speakerPoolRepository.save(speaker);

        // Publish state change event
        eventPublisher.publishEvent(new ch.batbern.shared.events.SpeakerWorkflowStateChangeEvent(
                speaker.getId(),
                speaker.getEventId(),
                previousState,
                ch.batbern.shared.types.SpeakerWorkflowState.QUALITY_REVIEWED,
                moderatorUsername
        ));

        // Check if speaker should auto-update to confirmed (AC17)
        checkAndUpdateToConfirmed(speaker);
    }

    /**
     * Reject speaker content with feedback.
     *
     * Status remains 'content_submitted'. Organizer can update session description and resubmit
     * for re-review (AC15).
     *
     * @param poolId the speaker pool ID
     * @param feedback the rejection feedback (required)
     * @param moderatorUsername the moderator rejecting the content
     * @throws IllegalArgumentException if feedback is missing
     */
    @Transactional
    public void rejectContent(String poolId, String feedback, String moderatorUsername) {
        log.info("Rejecting content for speaker pool entry: {} by moderator: {}", poolId, moderatorUsername);

        if (feedback == null || feedback.trim().isEmpty()) {
            throw new IllegalArgumentException("Feedback is required when rejecting content");
        }

        SpeakerPool speaker = speakerPoolRepository.findById(java.util.UUID.fromString(poolId))
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException(
                        "Speaker pool entry not found: " + poolId));

        // Update notes with rejection feedback
        String existingNotes = speaker.getNotes() != null ? speaker.getNotes() + "\n\n" : "";
        speaker.setNotes(existingNotes + feedback);

        // Status remains CONTENT_SUBMITTED (AC14)
        speakerPoolRepository.save(speaker);

        log.info("Content rejected for speaker pool entry: {} with feedback", poolId);
    }

    /**
     * Check if speaker should be auto-updated to 'confirmed' status.
     *
     * A speaker is confirmed when BOTH conditions are met (AC17):
     * - Status is 'quality_reviewed' (content approved)
     * - Session has start_time set (slot assigned)
     *
     * Order doesn't matter - quality review and slot assignment can happen in any order (AC16).
     *
     * Uses optimistic locking to prevent race conditions when multiple organizers work concurrently (AC35).
     *
     * @param speaker the speaker pool entry
     * @throws jakarta.persistence.OptimisticLockException if concurrent update detected (retry with fresh data)
     */
    void checkAndUpdateToConfirmed(SpeakerPool speaker) {
        log.debug("Checking if speaker {} should be updated to confirmed", speaker.getId());

        // Reload speaker to get fresh data (handles optimistic locking)
        SpeakerPool freshSpeaker = speakerPoolRepository.findById(speaker.getId())
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException(
                        "Speaker pool entry not found: " + speaker.getId()));

        // Check condition 1: Status is quality_reviewed
        boolean isQualityReviewed =
                freshSpeaker.getStatus() == ch.batbern.shared.types.SpeakerWorkflowState.QUALITY_REVIEWED;

        // Check condition 2: Session has start_time (slot assigned)
        boolean hasSlotAssigned = false;
        if (freshSpeaker.getSessionId() != null) {
            java.util.Optional<Session> sessionOpt = sessionRepository.findById(freshSpeaker.getSessionId());
            if (sessionOpt.isPresent() && sessionOpt.get().getStartTime() != null) {
                hasSlotAssigned = true;
            }
        }

        // Auto-update to confirmed when BOTH conditions met (AC17)
        if (isQualityReviewed && hasSlotAssigned) {
            log.info("Speaker {} meets confirmation criteria, updating status to CONFIRMED", freshSpeaker.getId());

            ch.batbern.shared.types.SpeakerWorkflowState previousState = freshSpeaker.getStatus();
            freshSpeaker.setStatus(ch.batbern.shared.types.SpeakerWorkflowState.CONFIRMED);
            speakerPoolRepository.save(freshSpeaker);

            // Update session_users.is_confirmed
            java.util.List<ch.batbern.events.domain.SessionUser> sessionUsers =
                    sessionUserRepository.findBySessionId(freshSpeaker.getSessionId());
            for (ch.batbern.events.domain.SessionUser sessionUser : sessionUsers) {
                sessionUser.confirm();
                sessionUserRepository.save(sessionUser);
            }

            // Publish state change event
            eventPublisher.publishEvent(new ch.batbern.shared.events.SpeakerWorkflowStateChangeEvent(
                    freshSpeaker.getId(),
                    freshSpeaker.getEventId(),
                    previousState,
                    ch.batbern.shared.types.SpeakerWorkflowState.CONFIRMED,
                    null  // System auto-update, not triggered by specific user
            ));

            log.info("Speaker {} successfully confirmed", freshSpeaker.getId());
        }
    }
}
