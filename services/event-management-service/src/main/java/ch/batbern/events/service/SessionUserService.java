package ch.batbern.events.service;

import ch.batbern.events.client.UserApiClient;
import ch.batbern.events.domain.Session;
import ch.batbern.events.domain.SessionUser;
import ch.batbern.events.domain.SessionUser.SpeakerRole;
import ch.batbern.events.dto.SessionSpeakerResponse;
import ch.batbern.events.dto.generated.users.UserResponse;
import ch.batbern.events.exception.SpeakerAssignmentNotFoundException;
import ch.batbern.events.exception.UserNotFoundException;
import ch.batbern.events.repository.SessionRepository;
import ch.batbern.events.repository.SessionUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
        UserResponse user = userApiClient.getUserByUsername(username);
        // If user doesn't exist, UserNotFoundException is thrown by API client

        // Check for duplicate assignment (ADR-003: use username)
        if (sessionUserRepository.existsBySessionIdAndUsername(sessionId, username)) {
            throw new IllegalArgumentException(
                    "User " + username + " is already assigned to session " + sessionId
            );
        }

        // Create SessionUser entity (ADR-003: username is the primary identifier)
        // Populate speaker name cache fields for full-text search (V38 migration)
        SessionUser sessionUser = SessionUser.builder()
                .session(session)
                .username(username)
                .speakerRole(speakerRole)
                .presentationTitle(presentationTitle)
                .speakerFirstName(user.getFirstName())  // Cache for full-text search
                .speakerLastName(user.getLastName())    // Cache for full-text search
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
     * @throws SpeakerAssignmentNotFoundException if assignment not found
     * @throws UserNotFoundException if user not found via API
     */
    public void removeSpeakerFromSession(UUID sessionId, String username) {
        log.info("Removing speaker {} from session {}", username, sessionId);

        // Find speaker assignment by username (ADR-003: meaningful identifier)
        SessionUser sessionUser = sessionUserRepository.findBySessionIdAndUsername(sessionId, username)
                .orElseThrow(() -> new SpeakerAssignmentNotFoundException(sessionId, username));

        sessionUserRepository.delete(sessionUser);

        log.info("Successfully removed speaker {} from session {}", username, sessionId);
    }

    /**
     * Confirm a speaker's participation in a session
     *
     * @param sessionId Session UUID
     * @param username User's username
     * @return Updated SessionSpeakerResponse
     * @throws SpeakerAssignmentNotFoundException if assignment not found
     * @throws UserNotFoundException if user not found via API
     */
    public SessionSpeakerResponse confirmSpeaker(UUID sessionId, String username) {
        log.info("Confirming speaker {} for session {}", username, sessionId);

        // Find speaker assignment by username (ADR-003: meaningful identifier)
        SessionUser sessionUser = sessionUserRepository.findBySessionIdAndUsername(sessionId, username)
                .orElseThrow(() -> new SpeakerAssignmentNotFoundException(sessionId, username));

        sessionUser.confirm();
        sessionUserRepository.save(sessionUser);

        log.info("Successfully confirmed speaker {} for session {}", username, sessionId);

        // Fetch user data via API for response enrichment
        UserResponse user = userApiClient.getUserByUsername(username);
        return enrichWithUserData(sessionUser, user);
    }

    /**
     * Decline a speaker's participation in a session
     *
     * @param sessionId Session UUID
     * @param username User's username
     * @param reason Reason for declining
     * @return Updated SessionSpeakerResponse
     * @throws SpeakerAssignmentNotFoundException if assignment not found
     * @throws UserNotFoundException if user not found via API
     */
    public SessionSpeakerResponse declineSpeaker(UUID sessionId, String username, String reason) {
        log.info("Declining speaker {} for session {} with reason: {}", username, sessionId, reason);

        // Find speaker assignment by username (ADR-003: meaningful identifier)
        SessionUser sessionUser = sessionUserRepository.findBySessionIdAndUsername(sessionId, username)
                .orElseThrow(() -> new SpeakerAssignmentNotFoundException(sessionId, username));

        sessionUser.decline(reason);
        sessionUserRepository.save(sessionUser);

        log.info("Successfully declined speaker {} for session {}", username, sessionId);

        // Fetch user data via API for response enrichment
        UserResponse user = userApiClient.getUserByUsername(username);
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
     * @param sessionUser SessionUser entity (must have username populated)
     * @return SessionSpeakerResponse with combined data
     * @throws UserNotFoundException if user not found via API (only for new assignments)
     */
    private SessionSpeakerResponse enrichWithUserData(SessionUser sessionUser) {
        // ADR-003: username is required for API-based user lookup
        if (sessionUser.getUsername() == null) {
            throw new IllegalStateException(
                "SessionUser must have username populated for API-based user lookup. "
                + "SessionUser ID: " + sessionUser.getId()
            );
        }

        try {
            // Fetch user data via API using username (cached for 15 minutes)
            UserResponse user = userApiClient.getUserByUsername(sessionUser.getUsername());
            return enrichWithUserData(sessionUser, user);
        } catch (UserNotFoundException e) {
            // Fallback for archived/historical speakers who no longer exist in the system
            // Use cached speaker name fields from SessionUser (populated during assignment)
            log.warn("User not found for speaker lookup, using cached data: {}", sessionUser.getUsername());

            return SessionSpeakerResponse.builder()
                    .username(sessionUser.getUsername())
                    .firstName(sessionUser.getSpeakerFirstName() != null
                            ? sessionUser.getSpeakerFirstName() : "Unknown")
                    .lastName(sessionUser.getSpeakerLastName() != null
                            ? sessionUser.getSpeakerLastName() : "Speaker")
                    .company(null) // No company data available for archived speakers
                    .profilePictureUrl(null) // No profile picture for archived speakers
                    .speakerRole(sessionUser.getSpeakerRole())
                    .presentationTitle(sessionUser.getPresentationTitle())
                    .isConfirmed(sessionUser.isConfirmed())
                    .build();
        }
    }

    /**
     * Enrich SessionUser with User data (when User is already loaded)
     * Combines session-user relationship data with user profile data
     */
    private SessionSpeakerResponse enrichWithUserData(SessionUser sessionUser, UserResponse user) {
        return SessionSpeakerResponse.builder()
                .username(user.getId())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .company(user.getCompanyId()) // companyId is the company name per Story 1.16.2
                .profilePictureUrl(user.getProfilePictureUrl() != null ? user.getProfilePictureUrl().toString() : null)
                .speakerRole(sessionUser.getSpeakerRole())
                .presentationTitle(sessionUser.getPresentationTitle())
                .isConfirmed(sessionUser.isConfirmed())
                .build();
    }

}
