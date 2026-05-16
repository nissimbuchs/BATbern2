package ch.batbern.events.service;

import ch.batbern.events.client.UserApiClient;
import ch.batbern.events.domain.ContentSubmission;
import ch.batbern.events.domain.Session;
import ch.batbern.events.domain.SessionMaterial;
import ch.batbern.events.domain.SessionUser;
import ch.batbern.events.domain.SpeakerPool;
import ch.batbern.events.dto.ContentDraftRequest;
import ch.batbern.events.dto.ContentDraftResponse;
import ch.batbern.events.dto.ContentSubmitResponse;
import ch.batbern.events.dto.SpeakerContentInfo;
import ch.batbern.events.dto.SpeakerContentResponse;
import ch.batbern.events.dto.TokenValidationResult;
import ch.batbern.events.dto.generated.users.PatchUserProfileRequest;
import ch.batbern.events.event.SpeakerContentSubmittedEvent;
import ch.batbern.events.repository.ContentSubmissionRepository;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.repository.SessionMaterialsRepository;
import ch.batbern.events.repository.SessionRepository;
import ch.batbern.events.repository.SessionUserRepository;
import ch.batbern.events.repository.SpeakerPoolRepository;
import ch.batbern.events.service.content.ContentSubmissionPayload;
import ch.batbern.events.service.workflow.SecurityPrincipal;
import ch.batbern.events.service.workflow.TransitionPayload;
import ch.batbern.shared.types.SpeakerWorkflowState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Consolidated content-submission service used by BOTH the organizer-on-behalf endpoint
 * ({@code POST /api/v1/events/{eventCode}/speakers/{speakerId}/content}) and the
 * speaker-self magic-link portal endpoint
 * ({@code POST /api/v1/speaker-portal/content/submit}).
 *
 * <p>Per ADR-009 §"Cross-cutting: two data-entry flows, one service layer" and Story
 * 11.C.2 (AC3/AC4/FR7), the two HTTP controllers stay separate (different auth scopes)
 * but they delegate to a single backend write path here. The principal-agnostic
 * {@link ContentSubmissionPayload} record is the unified shape both flows pass in.
 *
 * <p>Magic-link helpers ({@link #getContentInfo(String)} and {@link #saveDraft}) are
 * kept on this class until Phase E (Story 11.E.3) moves the portal to Cognito Bearer
 * auth and Phase F (Story 11.F.1) deletes the magic-link bridging code.
 *
 * <p>This service is the sole production caller of
 * {@code SpeakerWorkflowService.transition(CONTENT_SUBMITTED, ...)} — status mutation
 * and {@code speaker_status_history} writes are owned by
 * {@code SpeakerWorkflowService} per Story 11.B.2 AC1 (sole writer invariant). The
 * {@code CONTENT_SUBMITTED} arm of the side-effect switch is a no-op there — content
 * persistence and event publication happen here.
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
    private final SpeakerWorkflowService speakerWorkflowService;
    private final UserApiClient userApiClient;
    private final EventRepository eventRepository;

    // ============================================================
    // Magic-link portal helpers (kept until Phase E / Story 11.E.3)
    // ============================================================

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

        boolean hasSession = speaker.getSessionId() != null;
        String sessionTitle = null;
        Session session = null;

        if (hasSession) {
            Optional<Session> sessionOpt = sessionRepository.findById(speaker.getSessionId());
            if (sessionOpt.isPresent()) {
                session = sessionOpt.get();
                sessionTitle = session.getTitle();
            } else {
                hasSession = false;
            }
        }

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

        Optional<ContentSubmission> latestSubmission = contentSubmissionRepository
                .findFirstBySpeakerPoolIdOrderBySubmissionVersionDesc(speaker.getId());

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

        boolean hasMaterial = false;
        String materialUrl = null;
        String materialFileName = null;

        if (session != null) {
            List<SessionMaterial> materials = sessionMaterialsRepository.findBySession_Id(session.getId());
            if (!materials.isEmpty()) {
                hasMaterial = true;
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

        Optional<ContentSubmission> existingDraft = contentSubmissionRepository
                .findFirstBySpeakerPoolIdOrderBySubmissionVersionDesc(speaker.getId());

        ContentSubmission draft;
        String contentStatus = speaker.getContentStatus();
        boolean canUpdateExisting = "PENDING".equals(contentStatus) || "REVISION_NEEDED".equals(contentStatus);
        if (existingDraft.isPresent() && canUpdateExisting) {
            draft = existingDraft.get();
            draft.setTitle(truncate(request.title(), MAX_TITLE_LENGTH));
            draft.setContentAbstract(truncate(request.contentAbstract(), MAX_ABSTRACT_LENGTH));
            draft.setAbstractCharCount(request.contentAbstract() != null ? request.contentAbstract().length() : 0);
        } else {
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

    // ============================================================
    // Consolidated submit (Story 11.C.2 — AC3, AC4)
    // ============================================================

    /**
     * Shared content-submission write path used by both the organizer-on-behalf endpoint
     * and the speaker-self magic-link portal endpoint (Story 11.C.2 — AC4).
     *
     * <p>Behaviour:
     * <ol>
     *   <li>Validate title + contentAbstract (non-blank, length caps).</li>
     *   <li>Load {@link SpeakerPool} by id; 404 if missing.</li>
     *   <li>Pre-check source state: must be {@code ACCEPTED} or {@code CONTENT_SUBMITTED}
     *       (resubmission). Throws if other.</li>
     *   <li>Compute the next submission version.</li>
     *   <li>Get or create the session (slug-collision handled).</li>
     *   <li>Persist the new {@link ContentSubmission} row.</li>
     *   <li>If the payload carries a non-null bio or profilePictureUrl, call
     *       {@code UserApiClient.patchUserProfile(...)} (AR14). Skipped (with warning)
     *       when {@code speaker.getUsername()} is null — pre-11.B.2 legacy data.</li>
     *   <li>If presentationUploadId is non-null, link the uploaded file (deferred —
     *       see TODO inline).</li>
     *   <li>Delegate state transition to {@code SpeakerWorkflowService.transition(
     *       CONTENT_SUBMITTED, ...)} — sole writer per Story 11.B.2.</li>
     *   <li>Publish {@link SpeakerContentSubmittedEvent} (organizer notification).</li>
     * </ol>
     *
     * @param speakerPoolId speaker pool entry to submit content for
     * @param eventCode     event code (path parameter)
     * @param payload       principal-agnostic content payload (Story 11.C.2 — AC4)
     * @param principal     SPEAKER or ORGANIZER security principal (Story 11.B.2 type)
     * @return submission id + version + status + session title
     */
    @Transactional
    public ContentSubmitResponse submit(
            UUID speakerPoolId,
            String eventCode,
            ContentSubmissionPayload payload,
            SecurityPrincipal principal
    ) {
        // 1. Validate required fields (lightweight sanity — full @Valid is on the controller DTOs).
        if (payload == null) {
            throw new IllegalArgumentException("Content submission payload is required");
        }
        if (payload.title() == null || payload.title().isBlank()) {
            throw new IllegalArgumentException("Presentation title is required");
        }
        if (payload.contentAbstract() == null || payload.contentAbstract().isBlank()) {
            throw new IllegalArgumentException("Presentation abstract is required");
        }
        if (payload.title().length() > MAX_TITLE_LENGTH) {
            throw new IllegalArgumentException("Title exceeds maximum length of " + MAX_TITLE_LENGTH + " characters");
        }
        if (payload.contentAbstract().length() > MAX_ABSTRACT_LENGTH) {
            throw new IllegalArgumentException(
                    "Abstract exceeds maximum length of " + MAX_ABSTRACT_LENGTH + " characters");
        }

        log.info("Submitting content (Story 11.C.2): speakerPoolId={}, eventCode={}, actor={}",
                speakerPoolId, eventCode, principal.username());

        // 2. Load the speaker pool entry.
        SpeakerPool speaker = speakerPoolRepository.findById(speakerPoolId)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException(
                        "Speaker not found in pool: " + speakerPoolId));

        // 3. Pre-check the source state. SpeakerWorkflowService.transition() will also enforce
        //    this — we surface a friendly error message here before the workflow check runs.
        SpeakerWorkflowState currentStatus = speaker.getStatus();
        if (currentStatus != SpeakerWorkflowState.ACCEPTED
                && currentStatus != SpeakerWorkflowState.CONTENT_SUBMITTED) {
            throw new IllegalStateException(
                    "Cannot submit content — speaker must be in ACCEPTED or CONTENT_SUBMITTED state (was: "
                            + currentStatus + ")");
        }

        // 4. Determine next version (1 if first submission).
        Integer maxVersion = contentSubmissionRepository.findMaxVersionBySpeakerPoolId(speaker.getId());
        int newVersion = (maxVersion != null) ? maxVersion + 1 : 1;

        // 5. Get-or-create the session. Reuse existing session if speaker.sessionId is set and
        //    the row still exists; otherwise create a new one with slug-collision handling.
        Session session = getOrCreateSession(speaker, eventCode, payload.title().trim(),
                payload.contentAbstract().trim());

        // 6. Persist the new ContentSubmission row.
        ContentSubmission submission = ContentSubmission.builder()
                .speakerPool(speaker)
                .session(session)
                .title(payload.title().trim())
                .contentAbstract(payload.contentAbstract().trim())
                .abstractCharCount(payload.contentAbstract().trim().length())
                .submissionVersion(newVersion)
                .submittedAt(Instant.now())
                .build();
        submission = contentSubmissionRepository.save(submission);

        // 7. Patch user profile (bio / profilePictureUrl) if the payload carries either.
        //    Story 11.C.2 — AR14. The User profile is the single source of truth (ADR-007 +
        //    ADR-009 §"Decision 2"); patches overwrite globally.
        maybePatchUserProfile(speaker, payload);

        // 8. Link uploaded presentation material if a presentationUploadId is provided.
        //    TODO Story 11.D.4: factor out a principal-agnostic helper from
        //    SpeakerPortalMaterialsService.confirmUpload so the organizer-on-behalf form can
        //    attach uploaded files without going through the magic-link path. For 11.C.2 the
        //    field is accepted on the API surface; auto-linking lands with the organizer form.
        if (payload.presentationUploadId() != null && !payload.presentationUploadId().isBlank()) {
            log.info("presentationUploadId={} provided but auto-linking deferred to Story 11.D.4",
                    payload.presentationUploadId());
        }

        // 9. Keep the legacy content_status / content_submitted_at columns in sync (Phase F
        //    cleanup target). Workflow status mutation is owned by SpeakerWorkflowService.
        speaker.setContentStatus("SUBMITTED");
        speaker.setContentSubmittedAt(Instant.now());
        speakerPoolRepository.save(speaker);

        // 10. Delegate the workflow transition to the sole writer (Story 11.B.2 AC1). On a
        //     same-state CONTENT_SUBMITTED → CONTENT_SUBMITTED resubmission, transition()
        //     writes a self-transition history row and skips side-effect hooks (11.B.2 AC2/AC3).
        TransitionPayload transitionPayload = TransitionPayload.builder()
                .reason("Content submitted (version " + newVersion + ")")
                .build();
        speakerWorkflowService.transition(
                speaker.getId(),
                SpeakerWorkflowState.CONTENT_SUBMITTED,
                principal,
                transitionPayload
        );

        // 11. Publish the domain event consumed by OrganizerNotificationService. Payload mirrors
        //     what the legacy magic-link service emitted (Story 5.5 / 6.3 — unchanged shape).
        //     eventTitle is resolved from the Event row when available; eventCode is the
        //     canonical identifier and is always present.
        String eventTitle = eventRepository.findByEventCode(eventCode)
                .map(ch.batbern.events.domain.Event::getTitle)
                .orElse(eventCode);
        SpeakerContentSubmittedEvent contentEvent = new SpeakerContentSubmittedEvent(
                submission.getId(),
                speaker.getId(),
                speaker.getSpeakerName(),
                eventCode,
                eventTitle,
                session.getTitle(),
                payload.title().trim(),
                newVersion
        );
        eventPublisher.publishEvent(contentEvent);

        log.info("Content submitted: speakerPoolId={}, submissionId={}, version={}, actor={}",
                speaker.getId(), submission.getId(), newVersion, principal.username());

        return new ContentSubmitResponse(
                submission.getId(),
                newVersion,
                "SUBMITTED",
                session.getTitle()
        );
    }

    /**
     * Get-or-create the session for this speaker's content submission. Reuses the existing
     * session row if {@code speaker.sessionId} is set and the row still exists; otherwise
     * creates a new session with slug-collision handling (parallels the logic the previous
     * two services shared).
     */
    private Session getOrCreateSession(SpeakerPool speaker, String eventCode,
            String presentationTitle, String presentationAbstract) {
        // Reuse existing session if linked + still present.
        if (speaker.getSessionId() != null) {
            Optional<Session> existing = sessionRepository.findById(speaker.getSessionId());
            if (existing.isPresent()) {
                Session session = existing.get();
                session.setTitle(presentationTitle);
                session.setDescription(presentationAbstract);
                return sessionRepository.save(session);
            }
            log.warn("Session {} was deleted for speaker {} — creating a new session",
                    speaker.getSessionId(), speaker.getId());
            speaker.setSessionId(null);
        }

        // Generate a slug + handle collisions (parallels both legacy services).
        String baseSlug = presentationTitle
                .toLowerCase()
                .replaceAll("[^a-z0-9\\s-]", "")
                .replaceAll("\\s+", "-")
                .replaceAll("-+", "-")
                .replaceAll("^-|-$", "");
        if (baseSlug.length() > 200) {
            baseSlug = baseSlug.substring(0, 200);
        }
        if (baseSlug.isEmpty()) {
            baseSlug = "session-" + speaker.getId().toString().substring(0, 8);
        }

        String sessionSlug = baseSlug;
        int counter = 1;
        while (sessionRepository.existsBySessionSlug(sessionSlug)) {
            sessionSlug = baseSlug + "-" + counter;
            counter++;
            if (counter > 1000) {
                throw new IllegalStateException("Unable to generate unique session slug after 1000 attempts");
            }
        }

        Session session = Session.builder()
                .eventId(speaker.getEventId())
                .eventCode(eventCode)
                .sessionSlug(sessionSlug)
                .title(presentationTitle)
                .description(presentationAbstract)
                .sessionType("presentation")
                .build();
        session = sessionRepository.save(session);

        // Create session_users link (organizer side; speaker side has it idempotent via username).
        String usernameForLink = speaker.getUsername() != null && !speaker.getUsername().isBlank()
                ? speaker.getUsername()
                : speaker.getSpeakerName();
        SessionUser sessionUser = SessionUser.builder()
                .session(session)
                .username(usernameForLink)
                .speakerRole(SessionUser.SpeakerRole.PRIMARY_SPEAKER)
                .isConfirmed(false)
                .build();
        sessionUserRepository.save(sessionUser);

        // Bind the new session back to the speaker pool entry.
        speaker.setSessionId(session.getId());
        log.info("Created session {} for speaker {} on content submission",
                session.getId(), speaker.getId());
        return session;
    }

    /**
     * Patch the speaker's User profile (bio + profilePictureUrl) when the payload carries
     * non-null values. Skipped (with a warning) when {@code speaker.getUsername()} is null —
     * pre-11.B.2 legacy data where the speaker arrived at CONTENT_SUBMITTED without going
     * through a CONTACTED → READY provisioning.
     *
     * <p>Failure mode: content writes are independent of profile patches. If the User
     * Management Service is unavailable, we let the {@link ch.batbern.events.exception.UserServiceException}
     * propagate and roll back the @Transactional content write — content + profile must
     * stay consistent at the speaker's view. If integration tests later surface a real
     * rollback-asymmetry issue (Story 11.C.2 — Open Question §5), this call can be moved
     * to a post-commit listener; that decision is logged in the PR for whichever story
     * needs it.
     */
    private void maybePatchUserProfile(SpeakerPool speaker, ContentSubmissionPayload payload) {
        boolean hasBio = payload.bio() != null && !payload.bio().isBlank();
        boolean hasPictureUrl = payload.profilePictureUrl() != null && !payload.profilePictureUrl().isBlank();
        if (!hasBio && !hasPictureUrl) {
            return;
        }

        String username = speaker.getUsername();
        if (username == null || username.isBlank()) {
            log.warn("Skipping profile patch for speaker {} — username is null (pre-11.B.2 legacy"
                    + " speaker; expected to be set at CONTACTED → READY)", speaker.getId());
            return;
        }

        PatchUserProfileRequest request = new PatchUserProfileRequest();
        if (hasBio) {
            request.setBio(payload.bio());
        }
        if (hasPictureUrl) {
            request.setProfilePictureUrl(payload.profilePictureUrl());
        }
        userApiClient.patchUserProfile(username, request);
        log.debug("Patched user profile for {} (bio={}, pictureUrl={})",
                username, hasBio, hasPictureUrl);
    }

    // ============================================================
    // Organizer-side GET (moved from SpeakerContentSubmissionService — Story 11.C.2)
    // ============================================================

    /**
     * Get speaker content for an organizer-side view of a speaker pool entry. Handles
     * orphaned {@code session_id} references by detecting deleted sessions and clearing
     * the FK (Story 5.5 AC34); workflow status is left untouched (Story 11.B.2 single-writer
     * invariant).
     *
     * @param poolId the speaker pool ID
     * @return the speaker content (may have {@code hasContent=false} if no session)
     */
    @Transactional
    public SpeakerContentResponse getSpeakerContent(String poolId) {
        log.debug("Fetching speaker content for pool entry: {}", poolId);

        UUID poolUuid = UUID.fromString(poolId);
        SpeakerPool speaker = speakerPoolRepository.findById(poolUuid)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException(
                        "Speaker not found in pool"));

        if (speaker.getSessionId() == null) {
            return SpeakerContentResponse.builder()
                    .speakerPoolId(speaker.getId())
                    .eventId(speaker.getEventId())
                    .status(speaker.getStatus())
                    .speakerName(speaker.getSpeakerName())
                    .company(speaker.getCompany())
                    .hasContent(false)
                    .build();
        }

        Optional<Session> sessionOpt = sessionRepository.findById(speaker.getSessionId());

        if (sessionOpt.isEmpty()) {
            log.warn("Speaker {} references deleted session {}. Unlinking and resetting.",
                    poolId, speaker.getSessionId());
            speaker.setSessionId(null);
            speakerPoolRepository.save(speaker);

            return SpeakerContentResponse.builder()
                    .speakerPoolId(speaker.getId())
                    .eventId(speaker.getEventId())
                    .status(speaker.getStatus())
                    .speakerName(speaker.getSpeakerName())
                    .company(speaker.getCompany())
                    .hasContent(false)
                    .warning("Content was lost. Please resubmit.")
                    .build();
        }

        Session session = sessionOpt.get();
        List<SessionUser> sessionUsers = sessionUserRepository.findBySessionId(session.getId());
        String username = sessionUsers.isEmpty() ? null : sessionUsers.get(0).getUsername();

        ContentSubmission latestSubmission = contentSubmissionRepository
                .findFirstBySpeakerPoolIdOrderBySubmissionVersionDesc(speaker.getId())
                .orElse(null);

        String presentationTitle = latestSubmission != null
                ? latestSubmission.getTitle() : session.getTitle();
        String presentationAbstract = latestSubmission != null
                ? latestSubmission.getContentAbstract() : session.getDescription();

        boolean hasMaterial = false;
        String materialUrl = null;
        String materialFileName = null;

        List<SessionMaterial> materials = sessionMaterialsRepository
                .findBySession_IdOrderByCreatedAtAsc(session.getId());
        if (!materials.isEmpty()) {
            hasMaterial = true;
            SessionMaterial latestMaterial = materials.get(materials.size() - 1);
            materialUrl = latestMaterial.getCloudFrontUrl();
            materialFileName = latestMaterial.getFileName();
        }

        return SpeakerContentResponse.builder()
                .speakerPoolId(speaker.getId())
                .eventId(speaker.getEventId())
                .sessionId(session.getId())
                .presentationTitle(presentationTitle)
                .presentationAbstract(presentationAbstract)
                .username(username)
                .speakerName(speaker.getSpeakerName())
                .company(speaker.getCompany())
                .status(speaker.getStatus())
                .hasContent(true)
                .submittedAt(session.getCreatedAt())
                .hasMaterial(hasMaterial)
                .materialUrl(materialUrl)
                .materialFileName(materialFileName)
                .build();
    }

    // ============================================================
    // Helpers
    // ============================================================

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

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() > maxLength ? value.substring(0, maxLength) : value;
    }
}
