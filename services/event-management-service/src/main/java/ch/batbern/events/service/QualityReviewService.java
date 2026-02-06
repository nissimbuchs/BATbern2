package ch.batbern.events.service;

import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.Session;
import ch.batbern.events.domain.SpeakerPool;
import ch.batbern.events.repository.ContentSubmissionRepository;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.repository.SessionRepository;
import ch.batbern.events.repository.SessionUserRepository;
import ch.batbern.events.repository.SpeakerPoolRepository;
import ch.batbern.shared.service.EmailService;
import ch.batbern.shared.types.TokenAction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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
    private final ContentSubmissionRepository contentSubmissionRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final EmailService emailService;
    private final MagicLinkService magicLinkService;

    @Value("${app.base-url:https://batbern.ch}")
    private String baseUrl;

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
     * Updates contentStatus to REVISION_NEEDED and notifies speaker via email.
     * Speaker can revise and resubmit via portal (AC15).
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

        // Store rejection feedback with timestamp
        String timestamp = java.time.Instant.now().toString();
        String rejectionNote = String.format("[%s] REVISION REQUESTED by %s:\n%s",
                timestamp, moderatorUsername, feedback);
        String existingNotes = speaker.getNotes() != null ? speaker.getNotes() + "\n\n" : "";
        speaker.setNotes(existingNotes + rejectionNote);

        // Set contentStatus to REVISION_NEEDED so speaker knows to revise
        speaker.setContentStatus("REVISION_NEEDED");

        // Status remains CONTENT_SUBMITTED (AC14) - workflow state unchanged
        speakerPoolRepository.save(speaker);

        // Update latest ContentSubmission with reviewer feedback for portal display
        contentSubmissionRepository.findFirstBySpeakerPoolIdOrderBySubmissionVersionDesc(speaker.getId())
                .ifPresent(submission -> {
                    submission.setReviewerFeedback(feedback);
                    submission.setReviewedAt(java.time.Instant.now());
                    submission.setReviewedBy(moderatorUsername);
                    contentSubmissionRepository.save(submission);
                });

        // Notify speaker via email about required revisions
        notifySpeakerOfRejection(speaker, feedback);

        log.info("Content rejected for speaker pool entry: {} - speaker notified", poolId);
    }

    /**
     * Notify speaker that their content needs revision.
     * Sends email with feedback and magic link to the speaker portal.
     */
    private void notifySpeakerOfRejection(SpeakerPool speaker, String feedback) {
        if (speaker.getEmail() == null || speaker.getEmail().isBlank()) {
            log.warn("Cannot notify speaker {} - no email address", speaker.getId());
            return;
        }

        try {
            Event event = eventRepository.findById(speaker.getEventId())
                    .orElse(null);
            String eventName = event != null ? event.getTitle() : "BATbern Event";
            String speakerName = speaker.getSpeakerName() != null ? speaker.getSpeakerName() : "Speaker";

            // Generate a new magic link token for the speaker portal (30-day validity)
            String token = magicLinkService.generateToken(speaker.getId(), TokenAction.VIEW, 30);
            String portalUrl = baseUrl + "/speaker-portal/content?token=" + token;

            String subject = String.format("Action Required: Please revise your submission for %s", eventName);
            String body = buildRevisionEmailBody(speakerName, eventName, feedback, portalUrl);

            emailService.sendHtmlEmail(speaker.getEmail(), subject, body);
            log.info("Revision notification sent to speaker: {} with portal link", speaker.getEmail());
        } catch (Exception e) {
            log.error("Failed to send revision notification to speaker {}: {}",
                    speaker.getId(), e.getMessage());
        }
    }

    private String buildRevisionEmailBody(String speakerName, String eventName, String feedback, String portalUrl) {
        return String.format("""
            <html>
            <body>
            <p>Dear %s,</p>

            <p>Thank you for your submission for <strong>%s</strong>. Our review team has requested some revisions:</p>

            <div style="background-color: #f5f5f5; padding: 15px; margin: 15px 0; border-left: 4px solid #e74c3c;">
            <strong>Feedback:</strong><br/>
            %s
            </div>

            <p>Please click the button below to access the speaker portal and update your submission:</p>

            <p style="text-align: center; margin: 25px 0;">
            <a href="%s" style="background-color: #3498db; color: white; padding: 12px 30px; text-decoration: none; border-radius: 5px; font-weight: bold;">
            Revise My Submission
            </a>
            </p>

            <p style="font-size: 12px; color: #666;">
            Or copy and paste this link into your browser:<br/>
            <a href="%s">%s</a>
            </p>

            <p>If you have questions, please contact our team.</p>

            <p>Best regards,<br/>
            The BATbern Team</p>
            </body>
            </html>
            """, speakerName, eventName, feedback.replace("\n", "<br/>"), portalUrl, portalUrl, portalUrl);
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
