package ch.batbern.events.controller;

import ch.batbern.events.domain.Session;
import ch.batbern.events.dto.AssignSpeakerRequest;
import ch.batbern.events.dto.SessionSpeakerResponse;
import ch.batbern.events.dto.SpeakerConfirmationRequest;
import ch.batbern.events.exception.EventNotFoundException;
import ch.batbern.events.repository.SessionRepository;
import ch.batbern.events.service.SessionUserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST Controller for managing session speakers (session-user assignments)
 * Story 1.15a.1b: Session-User Many-to-Many Relationship
 *
 * Endpoints:
 * - POST   /api/v1/events/{eventCode}/sessions/{sessionSlug}/speakers
 * - GET    /api/v1/events/{eventCode}/sessions/{sessionSlug}/speakers
 * - DELETE /api/v1/events/{eventCode}/sessions/{sessionSlug}/speakers/{username}
 * - POST   /api/v1/events/{eventCode}/sessions/{sessionSlug}/speakers/{username}/confirm
 * - POST   /api/v1/events/{eventCode}/sessions/{sessionSlug}/speakers/{username}/decline
 */
@RestController
@RequestMapping("/api/v1/events/{eventCode}/sessions/{sessionSlug}/speakers")
@RequiredArgsConstructor
@Slf4j
public class SessionSpeakerController {

    private final SessionUserService sessionUserService;
    private final SessionRepository sessionRepository;

    /**
     * Assign a speaker to a session
     * POST /api/v1/events/{eventCode}/sessions/{sessionSlug}/speakers
     *
     * @param eventCode Event code (e.g., "BATbern56")
     * @param sessionSlug Session slug
     * @param request AssignSpeakerRequest with username and role
     * @return 201 Created with SessionSpeakerResponse
     */
    @PostMapping
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<SessionSpeakerResponse> assignSpeaker(
            @PathVariable String eventCode,
            @PathVariable String sessionSlug,
            @Valid @RequestBody AssignSpeakerRequest request
    ) {
        log.info("Assigning speaker {} to session {}/{} with role {}",
                request.getUsername(), eventCode, sessionSlug, request.getSpeakerRole());

        Session session = findSessionBySlug(eventCode, sessionSlug);

        SessionSpeakerResponse response = sessionUserService.assignSpeakerToSession(
                session.getId(),
                request.getUsername(),
                request.getSpeakerRole(),
                request.getPresentationTitle()
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * List all speakers for a session
     * GET /api/v1/events/{eventCode}/sessions/{sessionSlug}/speakers
     *
     * @param eventCode Event code
     * @param sessionSlug Session slug
     * @return 200 OK with list of SessionSpeakerResponse
     */
    @GetMapping
    public ResponseEntity<List<SessionSpeakerResponse>> listSpeakers(
            @PathVariable String eventCode,
            @PathVariable String sessionSlug
    ) {
        log.debug("Listing speakers for session {}/{}", eventCode, sessionSlug);

        Session session = findSessionBySlug(eventCode, sessionSlug);

        List<SessionSpeakerResponse> speakers = sessionUserService.getSessionSpeakers(session.getId());

        return ResponseEntity.ok(speakers);
    }

    /**
     * Remove a speaker from a session
     * DELETE /api/v1/events/{eventCode}/sessions/{sessionSlug}/speakers/{username}
     *
     * @param eventCode Event code
     * @param sessionSlug Session slug
     * @param username Speaker's username
     * @return 204 No Content
     */
    @DeleteMapping("/{username}")
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<Void> removeSpeaker(
            @PathVariable String eventCode,
            @PathVariable String sessionSlug,
            @PathVariable String username
    ) {
        log.info("Removing speaker {} from session {}/{}", username, eventCode, sessionSlug);

        Session session = findSessionBySlug(eventCode, sessionSlug);

        sessionUserService.removeSpeakerFromSession(session.getId(), username);

        return ResponseEntity.noContent().build();
    }

    /**
     * Confirm a speaker's participation
     * POST /api/v1/events/{eventCode}/sessions/{sessionSlug}/speakers/{username}/confirm
     *
     * @param eventCode Event code
     * @param sessionSlug Session slug
     * @param username Speaker's username
     * @return 200 OK with updated SessionSpeakerResponse
     */
    @PostMapping("/{username}/confirm")
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<SessionSpeakerResponse> confirmSpeaker(
            @PathVariable String eventCode,
            @PathVariable String sessionSlug,
            @PathVariable String username
    ) {
        log.info("Confirming speaker {} for session {}/{}", username, eventCode, sessionSlug);

        Session session = findSessionBySlug(eventCode, sessionSlug);

        SessionSpeakerResponse response = sessionUserService.confirmSpeaker(session.getId(), username);

        return ResponseEntity.ok(response);
    }

    /**
     * Decline a speaker's participation
     * POST /api/v1/events/{eventCode}/sessions/{sessionSlug}/speakers/{username}/decline
     *
     * @param eventCode Event code
     * @param sessionSlug Session slug
     * @param username Speaker's username
     * @param request SpeakerConfirmationRequest with optional decline reason
     * @return 200 OK with updated SessionSpeakerResponse
     */
    @PostMapping("/{username}/decline")
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<SessionSpeakerResponse> declineSpeaker(
            @PathVariable String eventCode,
            @PathVariable String sessionSlug,
            @PathVariable String username,
            @RequestBody SpeakerConfirmationRequest request
    ) {
        log.info("Declining speaker {} for session {}/{}", username, eventCode, sessionSlug);

        Session session = findSessionBySlug(eventCode, sessionSlug);

        SessionSpeakerResponse response = sessionUserService.declineSpeaker(
                session.getId(),
                username,
                request.getDeclineReason()
        );

        return ResponseEntity.ok(response);
    }

    /**
     * Helper method to find session by eventCode and sessionSlug
     * Note: Since Session only stores eventId (not eventCode), we find by slug only
     * The eventCode in the path is for API consistency but not used for lookup
     */
    private Session findSessionBySlug(String eventCode, String sessionSlug) {
        return sessionRepository.findBySessionSlug(sessionSlug)
                .orElseThrow(() -> new EventNotFoundException(
                        "Session not found: " + sessionSlug
                ));
    }
}
