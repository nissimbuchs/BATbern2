package ch.batbern.events.service;

import ch.batbern.events.client.UserApiClient;
import ch.batbern.events.domain.ContentSubmission;
import ch.batbern.events.domain.Session;
import ch.batbern.events.domain.SessionMaterial;
import ch.batbern.events.domain.SessionUser;
import ch.batbern.events.domain.SpeakerPool;
import ch.batbern.events.dto.SpeakerContentResponse;
import ch.batbern.events.repository.ContentSubmissionRepository;
import ch.batbern.events.repository.SessionMaterialsRepository;
import ch.batbern.events.repository.SessionRepository;
import ch.batbern.events.repository.SessionUserRepository;
import ch.batbern.events.repository.SpeakerPoolRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Service for speaker content submission workflow (Story 5.5 AC6-10).
 *
 * Handles:
 * - Presentation title/abstract submission
 * - Session creation and session_users junction link
 * - Portrait upload coordination (presigned URLs)
 * - Speaker pool status update (accepted → content_submitted)
 * - Event publication for workflow state changes
 *
 * Transaction Scope (AC33):
 * - Includes: Session creation, session_users link, speaker_pool update, event publication
 * - Excludes: Users-service calls (external), S3 presigned URL generation (idempotent)
 * - Rollback: All database changes rolled back on any exception
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SpeakerContentSubmissionService {

    private final SpeakerPoolRepository speakerPoolRepository;
    private final SessionRepository sessionRepository;
    private final SessionUserRepository sessionUserRepository;
    private final ContentSubmissionRepository contentSubmissionRepository;
    private final SessionMaterialsRepository sessionMaterialsRepository;
    private final UserApiClient userApiClient;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * Submit speaker content (presentation title and abstract).
     *
     * Transaction ensures atomicity: either all DB changes succeed or all are rolled back.
     * External calls (users-service) are made outside transaction to avoid long-running transactions.
     *
     * @param poolId the speaker pool ID
     * @param eventCode the event code (from path parameter) - Story 5.9: Used for persistent session.eventCode
     * @param presentationTitle the presentation title (required)
     * @param presentationAbstract the presentation abstract (required, max 1000 chars)
     * @param username the speaker username (if existing user)
     * @param speakerName the speaker name (if creating new user)
     * @param email the speaker email (if creating new user)
     * @param company the speaker company
     * @return the created session with updated speaker pool entry
     * @throws IllegalArgumentException if speaker is not in 'accepted' state (AC37)
     * @throws IllegalArgumentException if title or abstract is missing
     * @throws jakarta.persistence.EntityNotFoundException if speaker pool entry not found
     */
    @Transactional
    public SpeakerContentResponse submitContent(
            String poolId,
            String eventCode,
            String presentationTitle,
            String presentationAbstract,
            String username,
            String speakerName,
            String email,
            String company
    ) {
        log.info("Submitting content for speaker pool entry: {}", poolId);

        // AC6: Validate required fields
        if (presentationTitle == null || presentationTitle.isBlank()) {
            throw new IllegalArgumentException("Presentation title is required");
        }
        if (presentationAbstract == null || presentationAbstract.isBlank()) {
            throw new IllegalArgumentException("Presentation abstract is required");
        }

        // 1. Validate speaker_pool entry exists and is accepted (AC37)
        UUID poolUuid = UUID.fromString(poolId);
        SpeakerPool speaker = speakerPoolRepository.findById(poolUuid)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("Speaker not found in pool"));

        if (speaker.getStatus() != ch.batbern.shared.types.SpeakerWorkflowState.ACCEPTED) {
            throw new IllegalArgumentException("Speaker must be accepted before content submission");
        }

        // 2. Get username (use provided username directly - user management handled by frontend)
        // AC5: Store username in speaker_pool and session_users
        String finalUsername = username != null ? username : "unknown";

        // 3. Create session with title and description (AC7)
        // Generate session slug from title (URL-friendly)
        String baseSlug = presentationTitle
                .toLowerCase()
                .replaceAll("[^a-z0-9\\s-]", "")  // Remove special chars
                .replaceAll("\\s+", "-")           // Replace spaces with hyphens
                .replaceAll("-+", "-")             // Remove duplicate hyphens
                .replaceAll("^-|-$", "");          // Trim hyphens from start/end

        if (baseSlug.length() > 200) {
            baseSlug = baseSlug.substring(0, 200);
        }

        // Handle slug collisions by appending counter (e.g., "aaa-1", "aaa-2")
        // Note: session_slug is globally unique across all events (not scoped to event_id)
        String sessionSlug = baseSlug;
        int counter = 1;
        while (sessionRepository.existsBySessionSlug(sessionSlug)) {
            sessionSlug = baseSlug + "-" + counter;
            counter++;
            // Safety check to prevent infinite loops
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

        // 4. Create session_users link (AC8)
        SessionUser sessionUser = SessionUser.builder()
                .session(session)
                .username(finalUsername)
                .speakerRole(SessionUser.SpeakerRole.PRIMARY_SPEAKER)
                .isConfirmed(false) // AC8: Not confirmed until both quality_reviewed AND slot_assigned
                .build();
        sessionUserRepository.save(sessionUser);

        // 5. Update speaker_pool (AC10)
        ch.batbern.shared.types.SpeakerWorkflowState previousState = speaker.getStatus();
        speaker.setSessionId(session.getId());
        speaker.setStatus(ch.batbern.shared.types.SpeakerWorkflowState.CONTENT_SUBMITTED);
        speakerPoolRepository.save(speaker);

        // 6. Publish SpeakerWorkflowStateChangeEvent (AC10)
        ch.batbern.shared.events.SpeakerWorkflowStateChangeEvent event =
                new ch.batbern.shared.events.SpeakerWorkflowStateChangeEvent(
                        poolUuid,
                        speaker.getEventId(),
                        previousState,
                        ch.batbern.shared.types.SpeakerWorkflowState.CONTENT_SUBMITTED,
                        finalUsername
                );
        eventPublisher.publishEvent(event);

        log.info("Content submitted successfully for speaker pool entry {}, session {} created",
                poolId, session.getId());

        // Build response
        return SpeakerContentResponse.builder()
                .speakerPoolId(speaker.getId())
                .eventId(speaker.getEventId())
                .sessionId(session.getId())
                .presentationTitle(session.getTitle())
                .presentationAbstract(session.getDescription())
                .username(finalUsername)
                .speakerName(speaker.getSpeakerName())
                .company(speaker.getCompany())
                .status(speaker.getStatus())
                .hasContent(true)
                .submittedAt(session.getCreatedAt())
                .build();
    }

    /**
     * Get speaker content for a speaker pool entry.
     *
     * Handles orphaned session_id references (AC34) by detecting deleted sessions
     * and resetting speaker state to 'accepted' with warning.
     *
     * @param poolId the speaker pool ID
     * @return the speaker content or null if no content submitted
     */
    @Transactional
    public SpeakerContentResponse getSpeakerContent(String poolId) {
        log.debug("Fetching speaker content for pool entry: {}", poolId);

        // 1. Find speaker pool entry
        UUID poolUuid = UUID.fromString(poolId);
        SpeakerPool speaker = speakerPoolRepository.findById(poolUuid)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("Speaker not found in pool"));

        // 2. Check if session_id is null
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

        // 3. Check if session exists
        java.util.Optional<Session> sessionOpt = sessionRepository.findById(speaker.getSessionId());

        // 4. If session deleted (orphaned FK), reset speaker state and warn (AC34)
        if (sessionOpt.isEmpty()) {
            log.warn("Speaker {} references deleted session {}. Unlinking and resetting status.",
                    poolId, speaker.getSessionId());

            // Fix inconsistency
            speaker.setSessionId(null);
            if (speaker.getStatus() == ch.batbern.shared.types.SpeakerWorkflowState.CONTENT_SUBMITTED
                    || speaker.getStatus() == ch.batbern.shared.types.SpeakerWorkflowState.QUALITY_REVIEWED
                    || speaker.getStatus() == ch.batbern.shared.types.SpeakerWorkflowState.CONFIRMED) {
                speaker.setStatus(ch.batbern.shared.types.SpeakerWorkflowState.ACCEPTED);
            }
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

        // Session exists, return content
        Session session = sessionOpt.get();
        java.util.List<SessionUser> sessionUsers = sessionUserRepository.findBySessionId(session.getId());
        String username = sessionUsers.isEmpty() ? null : sessionUsers.get(0).getUsername();

        // Get the latest content submission for the actual submitted title/abstract
        ContentSubmission latestSubmission = contentSubmissionRepository
                .findFirstBySpeakerPoolIdOrderBySubmissionVersionDesc(speaker.getId())
                .orElse(null);

        // Use submitted content if available, otherwise fall back to session data
        String presentationTitle = latestSubmission != null
                ? latestSubmission.getTitle() : session.getTitle();
        String presentationAbstract = latestSubmission != null
                ? latestSubmission.getContentAbstract() : session.getDescription();

        // Get material info for the session
        // Security Note: CloudFront URLs are used for material delivery. These URLs are:
        // - Pre-event: Only accessible to authenticated organizers via this endpoint (requires ORGANIZER role)
        // - Post-publish: Publicly accessible via CDN after event is published
        // This is intentional - materials become public when event is published to website.
        // If stricter pre-event access control is needed, switch to S3 presigned URLs with expiration.
        boolean hasMaterial = false;
        String materialUrl = null;
        String materialFileName = null;

        List<SessionMaterial> materials = sessionMaterialsRepository.findBySession_Id(session.getId());
        if (!materials.isEmpty()) {
            hasMaterial = true;
            // Return the first/primary material (typically presentation)
            SessionMaterial primaryMaterial = materials.get(0);
            materialUrl = primaryMaterial.getCloudFrontUrl();
            materialFileName = primaryMaterial.getFileName();
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
}
