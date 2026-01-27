package ch.batbern.events.service;

import ch.batbern.events.domain.ContentSubmission;
import ch.batbern.events.domain.Session;
import ch.batbern.events.domain.SpeakerPool;
import ch.batbern.events.dto.ContentDraftRequest;
import ch.batbern.events.dto.ContentDraftResponse;
import ch.batbern.events.dto.ContentSubmitRequest;
import ch.batbern.events.dto.ContentSubmitResponse;
import ch.batbern.events.dto.SpeakerContentInfo;
import ch.batbern.events.dto.TokenValidationResult;
import ch.batbern.events.event.SpeakerContentSubmittedEvent;
import ch.batbern.events.repository.ContentSubmissionRepository;
import ch.batbern.events.repository.SessionRepository;
import ch.batbern.events.repository.SpeakerPoolRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
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
    private final ContentSubmissionRepository contentSubmissionRepository;
    private final ApplicationEventPublisher eventPublisher;

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

        if (!hasSession) {
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

        return SpeakerContentInfo.builder()
                .speakerName(validation.speakerName())
                .eventCode(validation.eventCode())
                .eventTitle(validation.eventTitle())
                .hasSessionAssigned(true)
                .sessionTitle(sessionTitle)
                .canSubmitContent(true)
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
        if (existingDraft.isPresent() && "PENDING".equals(speaker.getContentStatus())) {
            // Update existing draft
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

        // AC1: Check session assignment
        if (speaker.getSessionId() == null) {
            throw new IllegalStateException("No session assigned - contact organizer to assign your session");
        }

        Session session = sessionRepository.findById(speaker.getSessionId())
                .orElseThrow(() -> new IllegalStateException("Session not found - contact organizer"));

        // AC8: Determine version (increment if resubmitting)
        Integer maxVersion = contentSubmissionRepository.findMaxVersionBySpeakerPoolId(speaker.getId());
        int newVersion = (maxVersion != null) ? maxVersion + 1 : 1;

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

        // AC5: Update speaker pool content status
        speaker.setContentStatus("SUBMITTED");
        speaker.setContentSubmittedAt(Instant.now());
        speakerPoolRepository.save(speaker);

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
