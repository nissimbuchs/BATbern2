package ch.batbern.events.service;

import ch.batbern.events.client.UserApiClient;
import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.Session;
import ch.batbern.events.domain.SessionContentVersion;
import ch.batbern.events.domain.SessionMaterial;
import ch.batbern.events.domain.SessionUser;
import ch.batbern.events.domain.SpeakerPool;
import ch.batbern.events.dto.ContentSubmitResponse;
import ch.batbern.events.dto.SpeakerContentInfo;
import ch.batbern.events.dto.SpeakerContentResponse;
import ch.batbern.events.dto.generated.users.PatchUserProfileRequest;
import ch.batbern.events.event.SpeakerContentSubmittedEvent;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.repository.SessionContentHistoryRepository;
import ch.batbern.events.repository.SessionMaterialsRepository;
import ch.batbern.events.repository.SessionRepository;
import ch.batbern.events.repository.SessionUserRepository;
import ch.batbern.events.repository.SpeakerPoolRepository;
import ch.batbern.events.service.content.ContentSubmissionPayload;
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
 * <p>{@link #getContentInfo(SpeakerPool)} drives the speaker portal's read side; per
 * Story 11.E.8 §2.9 it returns {@code sessions.title}/{@code sessions.description} as
 * the canonical "current" so organizer-side edits to the session propagate instantly.
 * The legacy {@code saveDraft} method is gone — speaker-portal drafts live in
 * {@code localStorage} on the frontend, and {@link #submit} is the only backend write
 * path for content.
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

    private final SpeakerPoolRepository speakerPoolRepository;
    private final SessionRepository sessionRepository;
    private final SessionUserRepository sessionUserRepository;
    private final SessionContentHistoryRepository sessionContentHistoryRepository;
    private final SessionMaterialsRepository sessionMaterialsRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final SpeakerWorkflowService speakerWorkflowService;
    private final UserApiClient userApiClient;
    private final EventRepository eventRepository;

    // ============================================================
    // Speaker portal helpers — Story 11.E.3 (Cognito Bearer auth)
    // ============================================================

    /**
     * Get content information for the speaker portal.
     * Story 6.3 AC1: Session assignment check.
     * Story 6.3 AC4: Draft restoration.
     * Story 6.3 AC8: Revision feedback display.
     *
     * <p>Story 11.E.3: caller has resolved the speaker_pool row via
     * {@link SpeakerPortalAuthorizationService} (Cognito-authenticated path); the
     * event-context fields (eventCode, eventTitle, speakerName) come from the entity.
     *
     * @param speaker speaker_pool row (pre-resolved by the controller)
     * @return Speaker content info including session status and draft
     */
    @Transactional(readOnly = true)
    public SpeakerContentInfo getContentInfo(SpeakerPool speaker) {
        Event event = eventRepository.findById(speaker.getEventId())
                .orElseThrow(() -> new IllegalStateException(
                        "Event not found for speaker pool " + speaker.getId()));

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
                || speaker.getStatus() == SpeakerWorkflowState.CONTENT_SUBMITTED
                || speaker.getStatus() == SpeakerWorkflowState.QUALITY_REVIEWED;

        if (!canSubmit) {
            return SpeakerContentInfo.noSession(
                    speaker.getSpeakerName(),
                    event.getEventCode(),
                    event.getTitle()
            );
        }

        // Story 11.E.8 §2.9 consolidation: sessions.title / sessions.description are the
        // canonical "current" — every editor (speaker submit, organizer-on-behalf submit,
        // organizer session-edit modal) writes there. The speaker portal form must
        // initialise from the same canonical so an organizer edit propagates instantly.
        // The latest session_content_history row is only consulted for reviewer feedback
        // (REVISION_NEEDED) and the version counter shown on the timeline.
        Optional<SessionContentVersion> latestSubmission = session != null
                ? sessionContentHistoryRepository.findFirstBySessionIdOrderBySubmissionVersionDesc(session.getId())
                : Optional.empty();

        String contentStatus = ContentStatusDeriver.derive(speaker.getStatus(), latestSubmission);
        boolean needsRevision = "REVISION_NEEDED".equals(contentStatus);
        String reviewerFeedback = null;
        Instant reviewedAt = null;
        String reviewedBy = null;

        if (needsRevision && latestSubmission.isPresent()) {
            SessionContentVersion submission = latestSubmission.get();
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

        // Canonical title/abstract for the form come from sessions.title/description.
        // hasDraft remains true once a session exists (a placeholder title was written at
        // CONTACTED → READY), and the speaker portal uses it as the signal that the form
        // is editable. draftVersion / lastSavedAt reflect the latest history row so the
        // portal can show "v3 submitted on …" alongside the (potentially organizer-edited)
        // current title.
        String canonicalTitle = session != null ? session.getTitle() : null;
        String canonicalAbstract = session != null ? session.getDescription() : null;
        Instant canonicalUpdatedAt = session != null ? session.getUpdatedAt() : null;

        return SpeakerContentInfo.builder()
                .speakerName(speaker.getSpeakerName())
                .eventCode(event.getEventCode())
                .eventTitle(event.getTitle())
                .hasSessionAssigned(hasSession)
                .sessionTitle(sessionTitle != null ? sessionTitle : "Your Presentation")
                .canSubmitContent(canSubmit)
                // 2026-05-20 (Q#E) — `contentStatus` field dropped from the DTO; the
                // local `contentStatus` variable is retained as the source of
                // `needsRevision` below but no longer leaks to the wire.
                .hasDraft(session != null)
                .draftTitle(canonicalTitle)
                .draftAbstract(canonicalAbstract)
                .draftVersion(latestSubmission.map(SessionContentVersion::getSubmissionVersion).orElse(null))
                .lastSavedAt(latestSubmission.map(SessionContentVersion::getUpdatedAt).orElse(canonicalUpdatedAt))
                .needsRevision(needsRevision)
                .reviewerFeedback(reviewerFeedback)
                .reviewedAt(reviewedAt)
                .reviewedBy(reviewedBy)
                .hasMaterial(hasMaterial)
                .materialUrl(materialUrl)
                .materialFileName(materialFileName)
                .build();
    }

    // Story 11.E.8 §2.9 — backend saveDraft is gone. Drafts now live in the speaker
    // portal's localStorage. sessions.title / sessions.description are the single source
    // of truth for the canonical title and abstract; submit() is the only write path.

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
     *   <li>Persist the new {@link SessionContentVersion} row (Story 11.E.8 rename).</li>
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
     * @param username      username of the submitter (speaker or organizer-on-behalf); recorded in audit trail
     * @return submission id + version + status + session title
     */
    @Transactional
    public ContentSubmitResponse submit(
            UUID speakerPoolId,
            String eventCode,
            ContentSubmissionPayload payload,
            String username
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
                speakerPoolId, eventCode, username);

        // 2. Load the speaker pool entry.
        SpeakerPool speaker = speakerPoolRepository.findById(speakerPoolId)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException(
                        "Speaker not found in pool: " + speakerPoolId));

        // P1 (review patch): verify the speaker's eventId matches the eventCode in the URL.
        // Prevents cross-event corruption where a stale/mistyped URL pairs a speaker from
        // event A with eventCode = event B's code (the new Session row would then have
        // inconsistent event_id ↔ event_code).
        ch.batbern.events.domain.Event eventForCode = eventRepository.findByEventCode(eventCode)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException(
                        "Event not found for code: " + eventCode));
        if (!eventForCode.getId().equals(speaker.getEventId())) {
            throw new IllegalArgumentException(
                    "Speaker " + speakerPoolId + " does not belong to event " + eventCode
                            + " (speaker.eventId=" + speaker.getEventId()
                            + ", event.id=" + eventForCode.getId() + ")");
        }

        // 3. Pre-check the source state. SpeakerWorkflowService.transition() will also enforce
        //    this — we surface a friendly error message here before the workflow check runs.
        //    QUALITY_REVIEWED is accepted as a source state: a speaker (or organizer-on-behalf)
        //    can revise content after the moderator's review; the back-transition
        //    QUALITY_REVIEWED → CONTENT_SUBMITTED requeues the submission for re-review.
        SpeakerWorkflowState currentStatus = speaker.getStatus();
        if (currentStatus != SpeakerWorkflowState.ACCEPTED
                && currentStatus != SpeakerWorkflowState.CONTENT_SUBMITTED
                && currentStatus != SpeakerWorkflowState.QUALITY_REVIEWED) {
            throw new IllegalStateException(
                    "Cannot submit content — speaker must be in ACCEPTED, CONTENT_SUBMITTED, or "
                            + "QUALITY_REVIEWED state (was: " + currentStatus + ")");
        }

        // 4. Story 11.E.8: strict lookup — the session was provisioned at the CONTACTED → READY
        //    transition. If it's missing here, that's a data-integrity bug (out-of-band delete)
        //    rather than a legitimate first-time submission. Throw rather than silently re-create.
        Session session = loadAssignedSession(speaker, payload.title().trim(),
                payload.contentAbstract().trim());

        // 5. Determine next version (1 if first submission). Story 11.E.8: keyed by session_id.
        Integer maxVersion = sessionContentHistoryRepository.findMaxVersionBySessionId(session.getId());
        int newVersion = (maxVersion != null) ? maxVersion + 1 : 1;

        // 6. Persist the new SessionContentVersion row (renamed from ContentSubmission per V99).
        SessionContentVersion submission = SessionContentVersion.builder()
                .session(session)
                .title(payload.title().trim())
                .contentAbstract(payload.contentAbstract().trim())
                .abstractCharCount(payload.contentAbstract().trim().length())
                .submissionVersion(newVersion)
                .submittedByUsername(username)
                .submittedAt(Instant.now())
                .build();
        submission = sessionContentHistoryRepository.save(submission);

        // 7. Patch user profile (bio / profilePictureUrl) if the payload carries either.
        //    Story 11.C.2 — AR14. The User profile is the single source of truth (ADR-007 +
        //    ADR-009 §"Decision 2"); patches overwrite globally.
        maybePatchUserProfile(speaker, payload);

        // 8. Link uploaded presentation material if a presentationUploadId is provided.
        //    P2 (review patch): reject with 400 if non-blank. The auto-linking behaviour was
        //    silently a no-op (clients saw 201 but their upload was never associated). Until
        //    Story 11.D.4 wires the materials helper, fail closed so callers know it's not
        //    supported yet rather than thinking their deck was attached.
        if (payload.presentationUploadId() != null && !payload.presentationUploadId().isBlank()) {
            throw new IllegalArgumentException(
                    "presentationUploadId is not yet supported on this endpoint"
                            + " — Story 11.D.4 wires the materials helper");
        }

        // 9. (Story 11.E.8 removed) legacy speaker_pool.content_status / content_submitted_at
        //    writes are gone — V100 dropped those columns. contentStatus is now derived at
        //    read time from session_content_history.

        // 10. Delegate the workflow transition to the sole writer (Story 11.B.2 AC1). On a
        //     same-state CONTENT_SUBMITTED → CONTENT_SUBMITTED resubmission, transition()
        //     writes a self-transition history row and skips side-effect hooks (11.B.2 AC2/AC3).
        TransitionPayload transitionPayload = TransitionPayload.builder()
                .reason("Content submitted (version " + newVersion + ")")
                .build();
        speakerWorkflowService.transition(
                speaker.getId(),
                SpeakerWorkflowState.CONTENT_SUBMITTED,
                username,
                transitionPayload
        );

        // 11. Publish the domain event consumed by OrganizerNotificationService. Payload mirrors
        //     what the legacy magic-link service emitted (Story 5.5 / 6.3 — unchanged shape).
        //     eventTitle is taken from the already-loaded Event row.
        String eventTitle = eventForCode.getTitle() != null ? eventForCode.getTitle() : eventCode;
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
                speaker.getId(), submission.getId(), newVersion, username);

        return new ContentSubmitResponse(
                submission.getId(),
                newVersion,
                "SUBMITTED",
                session.getTitle()
        );
    }

    /**
     * Story 11.E.8: strict lookup of the {@link Session} provisioned at the
     * CONTACTED → READY transition. This method updates the placeholder title and
     * description that {@code SpeakerWorkflowService.provisionSessionAndPrimarySpeaker}
     * wrote at READY-time to the real presentation title and abstract.
     *
     * <p>Throws {@link IllegalStateException} if the speaker has no {@code session_id}
     * or the referenced session row is missing. Both conditions indicate a data-integrity
     * problem (speaker reached CONTENT_SUBMITTED without going through READY, or the
     * session was deleted out-of-band) rather than a legitimate first-time submission.
     * Pre-11.E.8 local-dev data is covered by the V96 backfill migration.
     */
    private Session loadAssignedSession(SpeakerPool speaker, String presentationTitle,
            String presentationAbstract) {
        UUID sessionId = speaker.getSessionId();
        if (sessionId == null) {
            throw new IllegalStateException(
                    "Speaker " + speaker.getId() + " has no session_id — must be promoted to "
                            + "READY before content submission (Story 11.E.8)");
        }
        Session session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalStateException(
                        "Speaker " + speaker.getId() + " references missing session " + sessionId
                                + " — data integrity issue; re-provision at READY"));
        session.setTitle(presentationTitle);
        session.setDescription(presentationAbstract);
        return sessionRepository.save(session);
    }

    /**
     * Patch the speaker's User profile (bio + profilePictureUrl) when the payload carries
     * non-null values. Skipped (with a warning) when {@code speaker.getUsername()} is null —
     * pre-11.B.2 legacy data where the speaker arrived at CONTENT_SUBMITTED without going
     * through a CONTACTED → READY provisioning.
     *
     * <p><b>Dual-write asymmetry (known issue, code review 2026-05-16):</b> this HTTP
     * PATCH against CUMS commits in the User Management Service immediately. If a later
     * step in the @Transactional submit() pipeline throws (e.g.
     * {@link ch.batbern.events.service.workflow.InvalidStateTransitionException} on a
     * concurrent state change), the local content row is rolled back but the CUMS-side
     * bio/profilePictureUrl update is NOT. The user observes a 5xx but sees their bio
     * has been changed. Move this call to a {@code @TransactionalEventListener(AFTER_COMMIT)}
     * to convert to eventual consistency once Story 11.D.4 / Phase E has a domain event
     * to fire from. Tracked in deferred-work.md.
     *
     * <p>If the User Management Service is unavailable BEFORE its commit, the
     * {@link ch.batbern.events.exception.UserServiceException} propagates and rolls back
     * the EMS @Transactional content write — atomic in that direction.
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

        // Story 11.E.8 §2.9: sessions.title / sessions.description are the canonical
        // "current" for every read surface. Don't override with the history row — that
        // re-introduces the divergence §2.9 closed (organizer session-modal edits would
        // stop propagating to the organizer drawer).
        String presentationTitle = session.getTitle();
        String presentationAbstract = session.getDescription();

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

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() > maxLength ? value.substring(0, maxLength) : value;
    }
}
