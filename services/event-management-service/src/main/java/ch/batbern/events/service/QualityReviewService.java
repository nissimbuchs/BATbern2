package ch.batbern.events.service;

import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.SessionContentVersion;
import ch.batbern.events.domain.SpeakerPool;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.repository.SessionContentHistoryRepository;
import ch.batbern.events.repository.SessionRepository;
import ch.batbern.events.repository.SpeakerPoolRepository;
import ch.batbern.events.service.workflow.TransitionPayload;
import ch.batbern.shared.service.EmailService;
import ch.batbern.shared.types.SpeakerWorkflowState;
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
    private final SessionContentHistoryRepository sessionContentHistoryRepository;
    private final EmailService emailService;
    private final SpeakerWorkflowService speakerWorkflowService;
    private final PrimarySpeakerResolver primarySpeakerResolver;

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

        TransitionPayload payload = TransitionPayload.builder()
                .reason("Content approved by moderator")
                .build();

        speakerWorkflowService.transition(
                speakerId, SpeakerWorkflowState.QUALITY_REVIEWED, moderatorUsername, payload);
    }

    /**
     * Reject speaker content with feedback.
     *
     * <p>Story 11.E.8 consolidation: the legacy {@code speaker_pool.content_status =
     * "REVISION_NEEDED"} write is gone — the column was dropped in V100. Reviewer
     * feedback is written onto the latest {@code session_content_history} row (the
     * version that's being rejected); {@link ContentStatusDeriver} now produces
     * {@code "REVISION_NEEDED"} at read time whenever the latest history row carries
     * non-empty {@code reviewerFeedback}.
     *
     * <p>Workflow state remains {@code CONTENT_SUBMITTED} (AC14) — the speaker has not
     * left "submitted" territory; they just need to revise. Resubmission creates a new
     * version row with no feedback, flipping the derived status back to {@code SUBMITTED}.
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

        // Audit trail — keep the human-readable note on speaker_pool.notes so the rejection
        // history is browsable independently of the version table.
        String timestamp = java.time.Instant.now().toString();
        String rejectionNote = String.format("[%s] REVISION REQUESTED by %s:%n%s",
                timestamp, moderatorUsername, feedback);
        String existingNotes = speaker.getNotes() != null ? speaker.getNotes() + "\n\n" : "";
        speaker.setNotes(existingNotes + rejectionNote);
        speakerPoolRepository.save(speaker);

        // Write reviewer feedback onto the latest history row. If no version exists yet
        // (organizer-path content where sessions.title was set directly), synthesize a
        // v1 row from the session data so the speaker portal can display the feedback
        // and pre-populate the revision form.
        if (speaker.getSessionId() == null) {
            log.warn("Cannot record rejection feedback for speaker {} — no session linked",
                    speaker.getId());
        } else {
            java.util.UUID sessionId = speaker.getSessionId();
            java.util.Optional<SessionContentVersion> latestSubmission =
                    sessionContentHistoryRepository.findFirstBySessionIdOrderBySubmissionVersionDesc(sessionId);
            if (latestSubmission.isPresent()) {
                SessionContentVersion submission = latestSubmission.get();
                submission.setReviewerFeedback(feedback);
                submission.setReviewedAt(java.time.Instant.now());
                submission.setReviewedBy(moderatorUsername);
                sessionContentHistoryRepository.save(submission);
            } else {
                sessionRepository.findById(sessionId).ifPresent(session -> {
                    SessionContentVersion submission = SessionContentVersion.builder()
                            .session(session)
                            .title(session.getTitle())
                            .contentAbstract(session.getDescription())
                            .abstractCharCount(session.getDescription() != null
                                    ? session.getDescription().length() : 0)
                            .submissionVersion(1)
                            .submittedByUsername(primarySpeakerResolver.resolve(speaker)
                                    .map(PrimarySpeakerResolver.PrimarySpeakerProfile::username)
                                    .filter(u -> u != null && !u.isBlank())
                                    .orElse(speaker.getSpeakerName()))
                            .submittedAt(java.time.Instant.now())
                            .reviewerFeedback(feedback)
                            .reviewedAt(java.time.Instant.now())
                            .reviewedBy(moderatorUsername)
                            .build();
                    sessionContentHistoryRepository.save(submission);
                    log.info("Created v1 session_content_history row from session for speaker: {}",
                            speaker.getId());
                });
            }
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
        // Story 11.E.9 (post-pool-email drop): recipient routing flows through
        // PrimarySpeakerResolver (session_users + UserApiClient); rejected content
        // always belongs to a speaker with a session (status CONTENT_SUBMITTED+),
        // so the resolve result is expected non-empty.
        java.util.Optional<PrimarySpeakerResolver.PrimarySpeakerProfile> primary =
                primarySpeakerResolver.resolve(speaker);
        String recipientEmail = primary
                .map(PrimarySpeakerResolver.PrimarySpeakerProfile::email)
                .filter(e -> e != null && !e.isBlank())
                .orElse(null);
        if (recipientEmail == null) {
            log.warn("Cannot notify speaker {} - no resolvable primary speaker email", speaker.getId());
            return;
        }

        try {
            Event event = eventRepository.findById(speaker.getEventId())
                    .orElse(null);
            String eventName = event != null ? event.getTitle() : "BATbern Event";
            String speakerName = primary
                    .map(PrimarySpeakerResolver.PrimarySpeakerProfile::fullName)
                    .filter(n -> !n.isEmpty())
                    .orElseGet(() -> speaker.getSpeakerName() != null ? speaker.getSpeakerName() : "Speaker");

            // Story 11.F.1 (Phase F): speakers now authenticate via Cognito — the
            // revision link points at the speaker-portal content route which is
            // SPEAKER-role-guarded server-side and picks up the Bearer from the session.
            String portalUrl = baseUrl + "/speaker-portal/content/" + (event != null ? event.getEventCode() : "");

            String subject = String.format("Action Required: Please revise your submission for %s", eventName);
            String body = buildRevisionEmailBody(speakerName, eventName, feedback, portalUrl);

            // Story 10.32: CC speaker's additional emails (empty list = unchanged behaviour)
            java.util.List<String> cc = primary
                    .map(PrimarySpeakerResolver.PrimarySpeakerProfile::additionalEmails)
                    .orElse(java.util.Collections.emptyList());
            emailService.sendHtmlEmail(recipientEmail, cc, subject, body);
            // Note: Don't log email address (PII) - only log speaker ID per GDPR data minimization
            log.info("Revision notification sent to speaker pool entry: {} with portal link (ccCount={})",
                    speaker.getId(), cc.size());
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
