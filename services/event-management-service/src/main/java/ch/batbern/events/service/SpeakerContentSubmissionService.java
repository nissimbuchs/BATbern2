package ch.batbern.events.service;

import ch.batbern.events.client.UserApiClient;
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
    private final UserApiClient userApiClient;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * Submit speaker content (presentation title and abstract).
     *
     * Transaction ensures atomicity: either all DB changes succeed or all are rolled back.
     * External calls (users-service) are made outside transaction to avoid long-running transactions.
     *
     * @param eventId the event ID
     * @param poolId the speaker pool ID
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
    public Session submitContent(
            String eventId,
            String poolId,
            String presentationTitle,
            String presentationAbstract,
            String username,
            String speakerName,
            String email,
            String company
    ) {
        log.info("Submitting content for speaker pool entry: {}", poolId);

        // TODO: Implement full content submission logic (Phase 3)
        // 1. Validate speaker_pool entry exists and is accepted (AC37)
        // 2. Lookup/create user in users-service (external call)
        // 3. Create session with title and description (AC7)
        // 4. Create session_users link (AC8)
        // 5. Update speaker_pool.session_id and status (AC10)
        // 6. Publish SpeakerWorkflowStateChangeEvent (AC10)

        throw new UnsupportedOperationException("Content submission not yet implemented");
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
    public Session getSpeakerContent(String poolId) {
        log.debug("Fetching speaker content for pool entry: {}", poolId);

        // TODO: Implement content retrieval (Phase 3)
        // 1. Find speaker pool entry
        // 2. Check if session_id is null
        // 3. If session exists, return session
        // 4. If session deleted (orphaned FK), reset speaker state and warn (AC34)

        throw new UnsupportedOperationException("Content retrieval not yet implemented");
    }
}
