package ch.batbern.events.service;

import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.SpeakerPool;
import ch.batbern.events.repository.ContentSubmissionRepository;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.repository.SessionRepository;
import ch.batbern.events.repository.SpeakerPoolRepository;
import ch.batbern.events.security.SecurityContextHelper;
import ch.batbern.events.service.workflow.SecurityPrincipal;
import ch.batbern.events.service.workflow.TransitionPayload;
import ch.batbern.shared.service.EmailService;
import ch.batbern.shared.types.SpeakerWorkflowState;
import ch.batbern.shared.types.TokenAction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.util.HtmlUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Service for quality review workflow (Story 5.5 AC11-15).
 *
 * <p>Story 11.B.2 (ADR-009): the state mutation to {@code QUALITY_REVIEWED} delegates to
 * {@link SpeakerWorkflowService#transition}. The legacy auto-confirm path is gone —
 * {@code CONFIRMED} no longer exists; the derived {@code is_publishable} predicate
 * ({@code QUALITY_REVIEWED AND slot_assigned}) is computed at read time (exposure lands
 * in 11.B.3).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QualityReviewService {

    private final EventRepository eventRepository;
    private final SpeakerPoolRepository speakerPoolRepository;
    private final SessionRepository sessionRepository;
    private final ContentSubmissionRepository contentSubmissionRepository;
    private final EmailService emailService;
    private final MagicLinkService magicLinkService;
    private final SpeakerWorkflowService speakerWorkflowService;
    private final SecurityContextHelper securityContextHelper;

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
     * Approve speaker content — delegates the state transition to
     * {@link SpeakerWorkflowService#transition} (sole writer per ADR-009).
     *
     * <p>The target state is {@code QUALITY_REVIEWED}; {@code CONFIRMED} is gone. The
     * "ready for agenda" predicate ({@code is_publishable = QUALITY_REVIEWED && slot_assigned})
     * is computed at read time (exposed in 11.B.3).
     *
     * @param poolId the speaker pool ID
     * @param moderatorUsername the moderator approving the content
     */
    @Transactional
    public void approveContent(String poolId, String moderatorUsername) {
        log.info("Approving content for speaker pool entry: {} by moderator: {}", poolId, moderatorUsername);

        java.util.UUID speakerId = java.util.UUID.fromString(poolId);
        if (!speakerPoolRepository.existsById(speakerId)) {
            throw new jakarta.persistence.EntityNotFoundException("Speaker pool entry not found: " + poolId);
        }

        SecurityPrincipal actor = new SecurityPrincipal(moderatorUsername, safeRoles());
        TransitionPayload payload = TransitionPayload.builder()
                .reason("Content approved by moderator")
                .build();

        speakerWorkflowService.transition(speakerId, SpeakerWorkflowState.QUALITY_REVIEWED, actor, payload);
    }

    private List<String> safeRoles() {
        try {
            return securityContextHelper.getCurrentUserRoles();
        } catch (SecurityException ex) {
            return List.of();
        }
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
        // If no ContentSubmission exists (organizer-path content), create one from session data
        // so the speaker portal can display the feedback and pre-populate the revision form
        java.util.Optional<ch.batbern.events.domain.ContentSubmission> latestSubmission =
                contentSubmissionRepository.findFirstBySpeakerPoolIdOrderBySubmissionVersionDesc(speaker.getId());
        if (latestSubmission.isPresent()) {
            ch.batbern.events.domain.ContentSubmission submission = latestSubmission.get();
            submission.setReviewerFeedback(feedback);
            submission.setReviewedAt(java.time.Instant.now());
            submission.setReviewedBy(moderatorUsername);
            contentSubmissionRepository.save(submission);
        } else if (speaker.getSessionId() != null) {
            // Create ContentSubmission from session data for organizer-path content
            sessionRepository.findById(speaker.getSessionId())
                    .ifPresent(session -> {
                        var submission = ch.batbern.events.domain.ContentSubmission
                                .builder()
                                .speakerPool(speaker)
                                .session(session)
                                .title(session.getTitle())
                                .contentAbstract(session.getDescription())
                                .abstractCharCount(session.getDescription() != null
                                        ? session.getDescription().length() : 0)
                                .submissionVersion(1)
                                .submittedAt(java.time.Instant.now())
                                .reviewerFeedback(feedback)
                                .reviewedAt(java.time.Instant.now())
                                .reviewedBy(moderatorUsername)
                                .build();
                        contentSubmissionRepository.save(submission);
                        log.info("Created ContentSubmission from session for speaker: {}",
                                speaker.getId());
                    });
        }

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

            // Generate a new magic link token for the speaker portal
            // 14-day validity aligns with typical revision deadline and reduces security exposure
            String token = magicLinkService.generateToken(speaker.getId(), TokenAction.VIEW, 14);
            String portalUrl = baseUrl + "/speaker-portal/content?token=" + token;

            String subject = String.format("Action Required: Please revise your submission for %s", eventName);
            String body = buildRevisionEmailBody(speakerName, eventName, feedback, portalUrl);

            emailService.sendHtmlEmail(speaker.getEmail(), subject, body);
            // Note: Don't log email address (PII) - only log speaker ID per GDPR data minimization
            log.info("Revision notification sent to speaker pool entry: {} with portal link", speaker.getId());
        } catch (Exception e) {
            log.error("Failed to send revision notification to speaker {}: {}",
                    speaker.getId(), e.getMessage());
        }
    }

    private String buildRevisionEmailBody(String speakerName, String eventName, String feedback, String portalUrl) {
        // Escape HTML in user-provided content to prevent XSS (defense in depth)
        String safeSpeakerName = HtmlUtils.htmlEscape(speakerName);
        String safeEventName = HtmlUtils.htmlEscape(eventName);
        String safeFeedback = HtmlUtils.htmlEscape(feedback).replace("\n", "<br/>");

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
            """, safeSpeakerName, safeEventName, safeFeedback, portalUrl, portalUrl, portalUrl);
    }

}
