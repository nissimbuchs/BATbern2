package ch.batbern.events.service;

import ch.batbern.events.domain.Session;
import ch.batbern.events.domain.SessionUser;
import ch.batbern.events.domain.SessionUser.SpeakerRole;
import ch.batbern.events.dto.SessionSpeakerResponse;
import ch.batbern.events.repository.SessionRepository;
import ch.batbern.events.repository.SessionUserRepository;
import ch.batbern.companyuser.domain.User;
import ch.batbern.companyuser.repository.UserRepository;
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
 * Implements ADR-004 pattern: references User entity directly via userId FK
 * Reduces cross-service dependencies by querying User repository directly
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class SessionUserService {

    private final SessionUserRepository sessionUserRepository;
    private final SessionRepository sessionRepository;
    private final UserRepository userRepository;

    /**
     * Assign a speaker to a session
     *
     * @param sessionId Session UUID
     * @param username User's username (public identifier per ADR-003/1.16.2)
     * @param speakerRole Role of the speaker
     * @param presentationTitle Optional speaker-specific presentation title
     * @return SessionSpeakerResponse with enriched user data
     * @throws IllegalArgumentException if session or user not found, or duplicate assignment
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

        // Validate user exists and get User entity
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + username));

        // Check for duplicate assignment
        if (sessionUserRepository.existsBySessionIdAndUserId(sessionId, user.getId())) {
            throw new IllegalArgumentException(
                    "User " + username + " is already assigned to session " + sessionId
            );
        }

        // Create SessionUser entity
        SessionUser sessionUser = SessionUser.builder()
                .session(session)
                .userId(user.getId())
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
     */
    public void removeSpeakerFromSession(UUID sessionId, String username) {
        log.info("Removing speaker {} from session {}", username, sessionId);

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + username));

        SessionUser sessionUser = sessionUserRepository.findBySessionIdAndUserId(sessionId, user.getId())
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
     */
    public SessionSpeakerResponse confirmSpeaker(UUID sessionId, String username) {
        log.info("Confirming speaker {} for session {}", username, sessionId);

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + username));

        SessionUser sessionUser = sessionUserRepository.findBySessionIdAndUserId(sessionId, user.getId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Speaker assignment not found for user " + username + " and session " + sessionId
                ));

        sessionUser.confirm();
        sessionUserRepository.save(sessionUser);

        log.info("Successfully confirmed speaker {} for session {}", username, sessionId);

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
     */
    public SessionSpeakerResponse declineSpeaker(UUID sessionId, String username, String reason) {
        log.info("Declining speaker {} for session {} with reason: {}", username, sessionId, reason);

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + username));

        SessionUser sessionUser = sessionUserRepository.findBySessionIdAndUserId(sessionId, user.getId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Speaker assignment not found for user " + username + " and session " + sessionId
                ));

        sessionUser.decline(reason);
        sessionUserRepository.save(sessionUser);

        log.info("Successfully declined speaker {} for session {}", username, sessionId);

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
     * ADR-004 pattern: User data comes from User entity, not duplicated
     *
     * @param sessionUser SessionUser entity
     * @return SessionSpeakerResponse with combined data
     */
    private SessionSpeakerResponse enrichWithUserData(SessionUser sessionUser) {
        User user = userRepository.findById(sessionUser.getUserId())
                .orElseThrow(() -> new IllegalStateException(
                        "User not found for SessionUser: " + sessionUser.getUserId()
                ));

        return enrichWithUserData(sessionUser, user);
    }

    /**
     * Enrich SessionUser with User data (when User is already loaded)
     */
    private SessionSpeakerResponse enrichWithUserData(SessionUser sessionUser, User user) {
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
}
