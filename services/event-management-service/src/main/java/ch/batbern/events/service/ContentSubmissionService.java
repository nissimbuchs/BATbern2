package ch.batbern.events.service;

import ch.batbern.events.domain.ContentSubmission;
import ch.batbern.events.domain.Session;
import ch.batbern.events.domain.SessionMaterial;
import ch.batbern.events.domain.SessionUser;
import ch.batbern.events.domain.SpeakerPool;
import ch.batbern.events.domain.SpeakerStatusHistory;
import ch.batbern.shared.types.SpeakerWorkflowState;
import ch.batbern.events.dto.ContentDraftRequest;
import ch.batbern.events.dto.ContentDraftResponse;
import ch.batbern.events.dto.ContentSubmitRequest;
import ch.batbern.events.dto.ContentSubmitResponse;
import ch.batbern.events.dto.SpeakerContentInfo;
import ch.batbern.events.dto.TokenValidationResult;
import ch.batbern.events.event.SpeakerContentSubmittedEvent;
import ch.batbern.events.repository.ContentSubmissionRepository;
import ch.batbern.events.repository.SessionMaterialsRepository;
import ch.batbern.events.repository.SessionRepository;
import ch.batbern.events.repository.SessionUserRepository;
import ch.batbern.events.repository.SpeakerPoolRepository;
import ch.batbern.events.repository.SpeakerStatusHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Service for speaker self-service content submission via magic link.
 * Story 6.3: Speaker Content Self-Submission Portal
 *
 * Handles:
 * - Content info retrieval (session assignment check, draft restoration)
 * - Draft saving (auto-save and manual save)
 * - Content submission (title, abstract with validation)
 * - Revision support (version increment, feedback display)
 *
 * Uses token-based authentication via MagicLinkService.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ContentSubmissionService {

    private static final int MAX_TITLE_LENGTH = 200;
    private static final int MAX_ABSTRACT_LENGTH = 1000;

    private final MagicLinkService magicLinkService;
    private final SpeakerPoolRepository speakerPoolRepository;
    private final SessionRepository sessionRepository;
    private final SessionUserRepository sessionUserRepository;
    private final ContentSubmissionRepository contentSubmissionRepository;
    private final SessionMaterialsRepository sessionMaterialsRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final SpeakerStatusHistoryRepository statusHistoryRepository;

    /**
     * Get content information for the speaker portal.
     * Story 6.3 AC1: Session assignment check
     * Story 6.3 AC4: Draft restoration
     * Story 6.3 AC8: Revision feedback display
     *
     * @param token Magic link token
     * @return Speaker content info including session status and draft
     * @throws IllegalArgumentException if token is invalid or expired
     */
    @Transactional(readOnly = true)
    public SpeakerContentInfo getContentInfo(String token) {
        TokenValidationResult validation = validateToken(token);

        SpeakerPool speaker = speakerPoolRepository.findById(validation.speakerPoolId())
                .orElseThrow(() -> new IllegalArgumentException("Speaker not found"));

        // AC1: Check session assignment
        boolean hasSession = speaker.getSessionId() != null;
        String sessionTitle = null;
        Session session = null;

        if (hasSession) {
            Optional<Session> sessionOpt = sessionRepository.findById(speaker.getSessionId());
            if (sessionOpt.isPresent()) {
                session = sessionOpt.get();
                sessionTitle = session.getTitle();
            } else {
                hasSession = false; // Session was deleted
            }
        }

        // ACCEPTED or CONTENT_SUBMITTED speakers can submit content even without a session
        // CONTENT_SUBMITTED covers the revision case where session may have been deleted
        // (session will be created on submission via SpeakerContentSubmissionService)
        boolean canSubmit = hasSession
                || speaker.getStatus() == SpeakerWorkflowState.ACCEPTED
                || speaker.getStatus() == SpeakerWorkflowState.CONTENT_SUBMITTED;

        if (!canSubmit) {
            return SpeakerContentInfo.noSession(
                    validation.speakerName(),
                    validation.eventCode(),
                    validation.eventTitle()
            );
        }

        // AC4: Get current draft/submission
        Optional<ContentSubmission> latestSubmission = contentSubmissionRepository
                .findFirstBySpeakerPoolIdOrderBySubmissionVersionDesc(speaker.getId());

        // AC8: Check if revision needed
        boolean needsRevision = "REVISION_NEEDED".equals(speaker.getContentStatus());
        String reviewerFeedback = null;
        Instant reviewedAt = null;
        String reviewedBy = null;

        if (needsRevision && latestSubmission.isPresent()) {
            ContentSubmission submission = latestSubmission.get();
            reviewerFeedback = submission.getReviewerFeedback();
            reviewedAt = submission.getReviewedAt();
            reviewedBy = submission.getReviewedBy();
        }

        // AC7: Get material info if session exists
        boolean hasMaterial = false;
        String materialUrl = null;
        String materialFileName = null;

        if (session != null) {
            List<SessionMaterial> materials = sessionMaterialsRepository.findBySession_Id(session.getId());
            if (!materials.isEmpty()) {
                hasMaterial = true;
                // Return the first/primary material (typically presentation)
                SessionMaterial primaryMaterial = materials.get(0);
                materialUrl = primaryMaterial.getCloudFrontUrl();
                materialFileName = primaryMaterial.getFileName();
            }
        }

        return SpeakerContentInfo.builder()
                .speakerName(validation.speakerName())
                .eventCode(validation.eventCode())
                .eventTitle(validation.eventTitle())
                .hasSessionAssigned(hasSession)
                .sessionTitle(sessionTitle != null ? sessionTitle : "Your Presentation")
                .canSubmitContent(canSubmit)
                .contentStatus(speaker.getContentStatus())
                .hasDraft(latestSubmission.isPresent())
                .draftTitle(latestSubmission.map(ContentSubmission::getTitle).orElse(null))
                .draftAbstract(latestSubmission.map(ContentSubmission::getContentAbstract).orElse(null))
                .draftVersion(latestSubmission.map(ContentSubmission::getSubmissionVersion).orElse(null))
                .lastSavedAt(latestSubmission.map(ContentSubmission::getUpdatedAt).orElse(null))
                .needsRevision(needsRevision)
                .reviewerFeedback(reviewerFeedback)
                .reviewedAt(reviewedAt)
                .reviewedBy(reviewedBy)
                .hasMaterial(hasMaterial)
                .materialUrl(materialUrl)
                .materialFileName(materialFileName)
                .build();
    }

    /**
     * Save content draft.
     * Story 6.3 AC4: Draft auto-save
     *
     * @param request Draft request with title and abstract
     * @return Draft response with saved timestamp
     * @throws IllegalArgumentException if token is invalid
     */
    @Transactional
    public ContentDraftResponse saveDraft(ContentDraftRequest request) {
        TokenValidationResult validation = validateToken(request.token());

        SpeakerPool speaker = speakerPoolRepository.findById(validation.speakerPoolId())
                .orElseThrow(() -> new IllegalArgumentException("Speaker not found"));

        // Get or create draft
        Optional<ContentSubmission> existingDraft = contentSubmissionRepository
                .findFirstBySpeakerPoolIdOrderBySubmissionVersionDesc(speaker.getId());

        ContentSubmission draft;
        String contentStatus = speaker.getContentStatus();
        boolean canUpdateExisting = "PENDING".equals(contentStatus) || "REVISION_NEEDED".equals(contentStatus);
        if (existingDraft.isPresent() && canUpdateExisting) {
            // Update existing draft (including revisions after rejection)
            draft = existingDraft.get();
            draft.setTitle(truncate(request.title(), MAX_TITLE_LENGTH));
            draft.setContentAbstract(truncate(request.contentAbstract(), MAX_ABSTRACT_LENGTH));
            draft.setAbstractCharCount(request.contentAbstract() != null ? request.contentAbstract().length() : 0);
        } else {
            // Create new draft
            Session session = speaker.getSessionId() != null
                    ? sessionRepository.findById(speaker.getSessionId()).orElse(null)
                    : null;

            Integer nextVersion = Optional.ofNullable(
                    contentSubmissionRepository.findMaxVersionBySpeakerPoolId(speaker.getId())
            ).map(v -> v + 1).orElse(1);

            draft = ContentSubmission.builder()
                    .speakerPool(speaker)
                    .session(session)
                    .title(truncate(request.title(), MAX_TITLE_LENGTH))
                    .contentAbstract(truncate(request.contentAbstract(), MAX_ABSTRACT_LENGTH))
                    .abstractCharCount(request.contentAbstract() != null ? request.contentAbstract().length() : 0)
                    .submissionVersion(nextVersion)
                    .build();
        }

        draft = contentSubmissionRepository.save(draft);

        log.info("Draft saved for speaker pool: {}, version: {}", speaker.getId(), draft.getSubmissionVersion());

        return new ContentDraftResponse(draft.getId(), draft.getUpdatedAt());
    }

    /**
     * Submit content for review.
     * Story 6.3 AC5: Content submission
     * Story 6.3 AC8: Version increment on resubmission
     *
     * @param request Submit request with title and abstract
     * @return Submit response with submission ID and version
     * @throws IllegalArgumentException if validation fails
     * @throws IllegalStateException if no session assigned
     */
    @Transactional
    public ContentSubmitResponse submitContent(ContentSubmitRequest request) {
        // Validate required fields
        if (request.title() == null || request.title().isBlank()) {
            throw new IllegalArgumentException("Presentation title is required");
        }
        if (request.contentAbstract() == null || request.contentAbstract().isBlank()) {
            throw new IllegalArgumentException("Presentation abstract is required");
        }
        if (request.title().length() > MAX_TITLE_LENGTH) {
            throw new IllegalArgumentException("Title exceeds maximum length of " + MAX_TITLE_LENGTH + " characters");
        }
        if (request.contentAbstract().length() > MAX_ABSTRACT_LENGTH) {
            throw new IllegalArgumentException(
                    "Abstract exceeds maximum length of " + MAX_ABSTRACT_LENGTH + " characters");
        }

        TokenValidationResult validation = validateToken(request.token());

        SpeakerPool speaker = speakerPoolRepository.findById(validation.speakerPoolId())
                .orElseThrow(() -> new IllegalArgumentException("Speaker not found"));

        // AC1: Get or create session
        Session session;
        boolean needsNewSession = speaker.getSessionId() == null;

        // Check if existing session was deleted (orphaned FK)
        if (!needsNewSession) {
            Optional<Session> existingSession = sessionRepository.findById(speaker.getSessionId());
            if (existingSession.isEmpty()) {
                log.warn("Session {} was deleted for speaker {} - will create new session",
                        speaker.getSessionId(), speaker.getId());
                speaker.setSessionId(null);
                needsNewSession = true;
            }
        }

        if (needsNewSession) {
            // Speakers without a session: create one on content submission
            if (speaker.getStatus() != SpeakerWorkflowState.ACCEPTED
                    && speaker.getStatus() != SpeakerWorkflowState.CONTENT_SUBMITTED) {
                throw new IllegalStateException(
                        "Cannot submit content - speaker must be in ACCEPTED or CONTENT_SUBMITTED state");
            }

            // Create session with presentation title and abstract
            String sessionSlug = request.title().trim().toLowerCase()
                    .replaceAll("[^a-z0-9]+", "-")
                    .replaceAll("^-|-$", "");

            session = Session.builder()
                    .eventId(speaker.getEventId())
                    .eventCode(validation.eventCode())
                    .sessionSlug(sessionSlug)
                    .title(request.title().trim())
                    .description(request.contentAbstract().trim())
                    .sessionType("presentation")
                    .build();
            session = sessionRepository.save(session);

            // Create session_users link
            SessionUser sessionUser = SessionUser.builder()
                    .session(session)
                    .username(speaker.getUsername())
                    .speakerRole(SessionUser.SpeakerRole.PRIMARY_SPEAKER)
                    .isConfirmed(false)
                    .build();
            sessionUserRepository.save(sessionUser);

            // Update speaker with session reference
            speaker.setSessionId(session.getId());

            log.info("Created new session {} for speaker {} on content submission",
                    session.getId(), speaker.getId());
        } else {
            session = sessionRepository.findById(speaker.getSessionId())
                    .orElseThrow(() -> new IllegalStateException("Session not found - contact organizer"));

            // Update session title and description from submitted content
            session.setTitle(request.title().trim());
            session.setDescription(request.contentAbstract().trim());
            session = sessionRepository.save(session);
        }

        // AC8: Determine version (increment if resubmitting)
        Integer maxVersion = contentSubmissionRepository.findMaxVersionBySpeakerPoolId(speaker.getId());
        int newVersion = (maxVersion != null) ? maxVersion + 1 : 1;

        // Capture previous status for history tracking
        SpeakerWorkflowState previousStatus = speaker.getStatus();

        // Create submission record
        ContentSubmission submission = ContentSubmission.builder()
                .speakerPool(speaker)
                .session(session)
                .title(request.title().trim())
                .contentAbstract(request.contentAbstract().trim())
                .abstractCharCount(request.contentAbstract().trim().length())
                .submissionVersion(newVersion)
                .submittedAt(Instant.now())
                .build();

        submission = contentSubmissionRepository.save(submission);

        // AC5: Update speaker pool status and content status
        Instant now = Instant.now();
        speaker.setStatus(SpeakerWorkflowState.CONTENT_SUBMITTED);  // Move to "Inhalt eingereicht" column in Kanban
        speaker.setContentStatus("SUBMITTED");
        speaker.setContentSubmittedAt(now);
        speakerPoolRepository.save(speaker);

        // Record status history for content submission
        if (previousStatus != SpeakerWorkflowState.CONTENT_SUBMITTED) {
            SpeakerStatusHistory statusHistory = new SpeakerStatusHistory();
            statusHistory.setSpeakerPoolId(speaker.getId());
            statusHistory.setEventId(session.getEventId());
            statusHistory.setSessionId(session.getId());
            statusHistory.setPreviousStatus(previousStatus);
            statusHistory.setNewStatus(SpeakerWorkflowState.CONTENT_SUBMITTED);
            String changedBy = speaker.getUsername() != null
                    ? speaker.getUsername() : validation.speakerName();
            statusHistory.setChangedByUsername(changedBy);
            statusHistory.setChangeReason("Content submitted via speaker portal (version " + newVersion + ")");
            statusHistory.setChangedAt(now);
            statusHistoryRepository.save(statusHistory);
            log.debug("Created status history for speaker {} transition to CONTENT_SUBMITTED", speaker.getId());
        }

        // AC6: Publish domain event for organizer notification
        SpeakerContentSubmittedEvent event = new SpeakerContentSubmittedEvent(
                submission.getId(),
                speaker.getId(),
                validation.speakerName(),
                validation.eventCode(),
                validation.eventTitle(),
                session.getTitle(),
                request.title().trim(),
                newVersion
        );
        eventPublisher.publishEvent(event);

        log.info("Content submitted for speaker pool: {}, submission: {}, version: {}",
                speaker.getId(), submission.getId(), newVersion);

        return new ContentSubmitResponse(
                submission.getId(),
                newVersion,
                "SUBMITTED",
                session.getTitle()
        );
    }

    /**
     * Validate token and throw if invalid.
     */
    private TokenValidationResult validateToken(String token) {
        TokenValidationResult result = magicLinkService.validateToken(token);

        if (!result.valid()) {
            String message = switch (result.error()) {
                case "NOT_FOUND" -> "Invalid token";
                case "EXPIRED" -> "Token has expired";
                case "ALREADY_USED" -> "Token has already been used";
                default -> "Token validation failed";
            };
            throw new IllegalArgumentException(message);
        }

        return result;
    }

    /**
     * Truncate string to max length.
     */
    private String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() > maxLength ? value.substring(0, maxLength) : value;
    }
}
