package ch.batbern.events.controller;

import ch.batbern.events.repository.SessionRepository;
import ch.batbern.events.repository.SessionUserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Security service for session-level authorization checks
 * Story 5.9 - Session Materials Upload (AC7: RBAC)
 *
 * Used by @PreAuthorize annotations in controllers to check if a user
 * has permission to modify a session (e.g., upload materials).
 *
 * Authorization Rules:
 * - Speakers can only modify sessions where they are assigned as speakers
 * - Organizers can modify any session (checked via role, not this service)
 */
@Service("sessionSecurityService")
public class SessionSecurityService {

    @Autowired
    private SessionRepository sessionRepository;

    @Autowired
    private SessionUserRepository sessionUserRepository;

    /**
     * Check if a user is a speaker of a session
     *
     * @param sessionSlug Session identifier
     * @param username Username to check
     * @return true if user is assigned as speaker to this session
     */
    public boolean isSpeakerOfSession(String sessionSlug, String username) {
        // Find session by slug
        return sessionRepository.findBySessionSlug(sessionSlug)
                .map(session -> {
                    // Check if user is assigned to this session
                    return sessionUserRepository.existsBySessionIdAndUsername(session.getId(), username);
                })
                .orElse(false);  // Session doesn't exist → deny access
    }
}
