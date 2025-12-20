package ch.batbern.events.service;

import ch.batbern.events.domain.Session;
import ch.batbern.events.domain.SpeakerPool;
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

    private final SpeakerPoolRepository speakerPoolRepository;
    private final SessionRepository sessionRepository;
    private final SessionUserRepository sessionUserRepository;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * Get review queue for an event.
     *
     * Returns all speakers with status='content_submitted', sorted by submission date (oldest first).
     *
     * @param eventId the event ID
     * @return list of speakers pending review (AC11)
     */
    public List<SpeakerPool> getReviewQueue(String eventId) {
        log.debug("Fetching review queue for event: {}", eventId);

        // TODO: Implement review queue (Phase 4)
        // 1. Find all speaker_pool entries with status='content_submitted'
        // 2. Sort by created_at (oldest first)
        // 3. Join with sessions to get title and abstract
        // 4. Return review queue DTOs

        throw new UnsupportedOperationException("Review queue not yet implemented");
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

        // TODO: Implement content approval (Phase 4)
        // 1. Find speaker pool entry
        // 2. Update status to 'quality_reviewed'
        // 3. Check if speaker also has slot assigned → auto-update to 'confirmed' (AC17)
        // 4. Update session_users.is_confirmed if confirmed
        // 5. Publish SpeakerWorkflowStateChangeEvent
        // 6. Handle OptimisticLockException with retry (AC35)

        throw new UnsupportedOperationException("Content approval not yet implemented");
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

        // TODO: Implement content rejection (Phase 4)
        // 1. Validate feedback is provided
        // 2. Find speaker pool entry
        // 3. Update notes with feedback
        // 4. Status remains 'content_submitted'
        // 5. Notify organizer of rejection (AC14)

        throw new UnsupportedOperationException("Content rejection not yet implemented");
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

        // TODO: Implement confirmed status check (Phase 4)
        // 1. Check if status is 'quality_reviewed'
        // 2. Check if session has start_time (slot assigned)
        // 3. If both true, update to 'confirmed'
        // 4. Update session_users.is_confirmed to true
        // 5. Publish SpeakerConfirmedEvent
        // 6. Handle OptimisticLockException with retry (AC35)

        throw new UnsupportedOperationException("Confirmed status check not yet implemented");
    }
}
