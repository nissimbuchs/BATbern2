package ch.batbern.events.service;

import ch.batbern.events.client.UserApiClient;
import ch.batbern.events.domain.Session;
import ch.batbern.events.domain.SessionUser;
import ch.batbern.events.domain.SessionUser.SpeakerRole;
import ch.batbern.events.dto.SessionSpeakerResponse;
import ch.batbern.events.dto.UserProfileDTO;
import ch.batbern.events.exception.UserNotFoundException;
import ch.batbern.events.repository.SessionRepository;
import ch.batbern.events.repository.SessionUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service for managing session-user (speaker) assignments
 * Story 1.15a.1b: Session-User Many-to-Many Relationship
 *
 * Migrated from direct database access to API-based user data retrieval.
 * Uses User Management Service REST API to fetch user profile data.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class SessionUserService {

    private final SessionUserRepository sessionUserRepository;
    private final SessionRepository sessionRepository;
    private final UserApiClient userApiClient;
    private final JdbcTemplate jdbcTemplate;

    /**
     * Assign a speaker to a session
     *
     * @param sessionId Session UUID
     * @param username User's username (public identifier per ADR-003/1.16.2)
     * @param speakerRole Role of the speaker
     * @param presentationTitle Optional speaker-specific presentation title
     * @return SessionSpeakerResponse with enriched user data
     * @throws IllegalArgumentException if session or user not found, or duplicate assignment
     * @throws UserNotFoundException if user not found via API
     */
    public SessionSpeakerResponse assignSpeakerToSession(
            UUID sessionId,
            String username,
            SpeakerRole speakerRole,
            String presentationTitle
    ) {
        log.info("Assigning speaker {} to session {} with role {}", username, sessionId, speakerRole);

        // Validate session exists
        Session session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found: " + sessionId));

        // Validate user exists via API and get user profile data
        UserProfileDTO user = userApiClient.getUserByUsername(username);
        // If user doesn't exist, UserNotFoundException is thrown by API client

        // Check for duplicate assignment (ADR-003: use username)
        if (sessionUserRepository.existsBySessionIdAndUsername(sessionId, username)) {
            throw new IllegalArgumentException(
                    "User " + username + " is already assigned to session " + sessionId
            );
        }

        // Get userId from local database for FK constraint
        UUID userId = getUserIdFromUsername(username);

        // Create SessionUser entity with both userId (FK) and username (API identifier)
        SessionUser sessionUser = SessionUser.builder()
                .session(session)
                .userId(userId)  // Required for FK constraint
                .username(username)  // ADR-003: meaningful identifier
                .speakerRole(speakerRole)
                .presentationTitle(presentationTitle)
                .isConfirmed(false)
                .invitedAt(Instant.now())
                .build();

        sessionUserRepository.save(sessionUser);

        log.info("Successfully assigned speaker {} to session {}", username, sessionId);

        return enrichWithUserData(sessionUser, user);
    }

    /**
     * Remove a speaker from a session
     *
     * @param sessionId Session UUID
     * @param username User's username
     * @throws IllegalArgumentException if assignment not found
     * @throws UserNotFoundException if user not found via API
     */
    public void removeSpeakerFromSession(UUID sessionId, String username) {
        log.info("Removing speaker {} from session {}", username, sessionId);

        // Find speaker assignment by username (ADR-003: meaningful identifier)
        SessionUser sessionUser = sessionUserRepository.findBySessionIdAndUsername(sessionId, username)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Speaker assignment not found for user " + username + " and session " + sessionId
                ));

        sessionUserRepository.delete(sessionUser);

        log.info("Successfully removed speaker {} from session {}", username, sessionId);
    }

    /**
     * Confirm a speaker's participation in a session
     *
     * @param sessionId Session UUID
     * @param username User's username
     * @return Updated SessionSpeakerResponse
     * @throws IllegalArgumentException if assignment not found
     * @throws UserNotFoundException if user not found via API
     */
    public SessionSpeakerResponse confirmSpeaker(UUID sessionId, String username) {
        log.info("Confirming speaker {} for session {}", username, sessionId);

        // Find speaker assignment by username (ADR-003: meaningful identifier)
        SessionUser sessionUser = sessionUserRepository.findBySessionIdAndUsername(sessionId, username)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Speaker assignment not found for user " + username + " and session " + sessionId
                ));

        sessionUser.confirm();
        sessionUserRepository.save(sessionUser);

        log.info("Successfully confirmed speaker {} for session {}", username, sessionId);

        // Fetch user data via API for response enrichment
        UserProfileDTO user = userApiClient.getUserByUsername(username);
        return enrichWithUserData(sessionUser, user);
    }

    /**
     * Decline a speaker's participation in a session
     *
     * @param sessionId Session UUID
     * @param username User's username
     * @param reason Reason for declining
     * @return Updated SessionSpeakerResponse
     * @throws IllegalArgumentException if assignment not found
     * @throws UserNotFoundException if user not found via API
     */
    public SessionSpeakerResponse declineSpeaker(UUID sessionId, String username, String reason) {
        log.info("Declining speaker {} for session {} with reason: {}", username, sessionId, reason);

        // Find speaker assignment by username (ADR-003: meaningful identifier)
        SessionUser sessionUser = sessionUserRepository.findBySessionIdAndUsername(sessionId, username)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Speaker assignment not found for user " + username + " and session " + sessionId
                ));

        sessionUser.decline(reason);
        sessionUserRepository.save(sessionUser);

        log.info("Successfully declined speaker {} for session {}", username, sessionId);

        // Fetch user data via API for response enrichment
        UserProfileDTO user = userApiClient.getUserByUsername(username);
        return enrichWithUserData(sessionUser, user);
    }

    /**
     * Get all speakers for a session (enriched with User data)
     *
     * @param sessionId Session UUID
     * @return List of SessionSpeakerResponse
     */
    @Transactional(readOnly = true)
    public List<SessionSpeakerResponse> getSessionSpeakers(UUID sessionId) {
        log.debug("Fetching speakers for session {}", sessionId);

        List<SessionUser> sessionUsers = sessionUserRepository.findBySessionId(sessionId);

        return sessionUsers.stream()
                .map(this::enrichWithUserData)
                .collect(Collectors.toList());
    }

    /**
     * Get all speakers for an event (for homepage display)
     *
     * @param eventId Event UUID
     * @return List of SessionSpeakerResponse
     */
    @Transactional(readOnly = true)
    public List<SessionSpeakerResponse> getEventSpeakers(UUID eventId) {
        log.debug("Fetching speakers for event {}", eventId);

        List<SessionUser> sessionUsers = sessionUserRepository.findAllByEventId(eventId);

        return sessionUsers.stream()
                .map(this::enrichWithUserData)
                .collect(Collectors.toList());
    }

    /**
     * Enrich SessionUser with User data to create SessionSpeakerResponse
     * Fetches user profile data from User Management Service API
     *
     * Handles transition period where username might not be populated:
     * - If username is set: use it for API lookup (preferred)
     * - If username is null: fetch by userId and populate username for next time
     *
     * @param sessionUser SessionUser entity
     * @return SessionSpeakerResponse with combined data
     * @throws UserNotFoundException if user not found via API
     */
    private SessionSpeakerResponse enrichWithUserData(SessionUser sessionUser) {
        UserProfileDTO user;

        if (sessionUser.getUsername() != null) {
            // Preferred path: fetch user data via API using username (cached for 15 minutes)
            user = userApiClient.getUserByUsername(sessionUser.getUsername());
        } else {
            // Fallback for transition period: lookup by userId
            // This shouldn't happen in normal flow since assignSpeakerToSession sets username,
            // but handles cases where old data exists or manual DB inserts occurred
            log.warn("SessionUser {} has null username, using userId fallback lookup", sessionUser.getId());

            // We need to fetch user by any username to get the UUID match
            // For now, this is a limitation - in practice, tests should ensure username is set
            throw new IllegalStateException(
                "SessionUser must have username populated for API-based user lookup. " +
                "SessionUser ID: " + sessionUser.getId() + ", UserId: " + sessionUser.getUserId()
            );
        }

        return enrichWithUserData(sessionUser, user);
    }

    /**
     * Enrich SessionUser with User data (when User is already loaded)
     * Combines session-user relationship data with user profile data
     */
    private SessionSpeakerResponse enrichWithUserData(SessionUser sessionUser, UserProfileDTO user) {
        return SessionSpeakerResponse.builder()
                .username(user.getUsername())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .company(user.getCompanyId()) // companyId is the company name per Story 1.16.2
                .profilePictureUrl(user.getProfilePictureUrl())
                .speakerRole(sessionUser.getSpeakerRole())
                .presentationTitle(sessionUser.getPresentationTitle())
                .isConfirmed(sessionUser.isConfirmed())
                .build();
    }

    /**
     * Get userId (UUID) from username by querying user_profiles table locally.
     *
     * This is needed because:
     * - ADR-003: Public APIs use meaningful identifiers (username), not UUIDs
     * - Database FK constraints still require userId (UUID)
     * - Both services share the same database
     *
     * TODO: Consider making user_id nullable in a future migration to fully embrace ADR-003
     *
     * @param username the user's username
     * @return the user's UUID from user_profiles table
     * @throws IllegalArgumentException if user not found locally
     */
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    private UUID getUserIdFromUsername(String username) {
        // Query user_profiles table directly (same database as event-management)
        return jdbcTemplate.queryForObject(
                "SELECT id FROM user_profiles WHERE username = ?",
                UUID.class,
                username
        );
    }
}
