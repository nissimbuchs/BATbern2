package ch.batbern.events.controller;

import ch.batbern.events.domain.Session;
import ch.batbern.events.domain.SessionUser.SpeakerRole;
import ch.batbern.events.exception.EventNotFoundException;
import ch.batbern.events.repository.SessionRepository;
import ch.batbern.events.service.SessionUserService;
import ch.batbern.events.sessions.api.generated.SessionSpeakersApi;
import ch.batbern.events.sessions.dto.generated.AssignSpeakerToSessionRequest;
import ch.batbern.events.sessions.dto.generated.DeclineSpeakerRequest;
import ch.batbern.events.sessions.dto.generated.SessionSpeaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST Controller for managing session speakers (session-user assignments).
 * Story 1.15a.1b: Session-User Many-to-Many Relationship.
 *
 * <p>Implements the generated {@link SessionSpeakersApi} contract (Phase 7
 * contract-first wiring) — the interface carries the {@code @RequestMapping}
 * annotations, paths, and bean-validation, so this class only supplies
 * {@code /api/v1} as the prefix and the method bodies.
 *
 * <p>Endpoints:
 * <ul>
 *   <li>POST   /api/v1/events/{eventCode}/sessions/{sessionSlug}/speakers</li>
 *   <li>GET    /api/v1/events/{eventCode}/sessions/{sessionSlug}/speakers</li>
 *   <li>DELETE /api/v1/events/{eventCode}/sessions/{sessionSlug}/speakers/{username}</li>
 *   <li>POST   /api/v1/events/{eventCode}/sessions/{sessionSlug}/speakers/{username}/confirm</li>
 *   <li>POST   /api/v1/events/{eventCode}/sessions/{sessionSlug}/speakers/{username}/decline</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Slf4j
public class SessionSpeakerController implements SessionSpeakersApi {

    private final SessionUserService sessionUserService;
    private final SessionRepository sessionRepository;

    @Override
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<SessionSpeaker> assignSpeakerToSession(
            String eventCode,
            String sessionSlug,
            AssignSpeakerToSessionRequest assignSpeakerToSessionRequest
    ) {
        log.info("Assigning speaker {} to session {}/{} with role {}",
                assignSpeakerToSessionRequest.getUsername(), eventCode, sessionSlug,
                assignSpeakerToSessionRequest.getSpeakerRole());

        Session session = findSessionBySlug(eventCode, sessionSlug);

        SessionSpeaker response = sessionUserService.assignSpeakerToSession(
                session.getId(),
                assignSpeakerToSessionRequest.getUsername(),
                SpeakerRole.valueOf(assignSpeakerToSessionRequest.getSpeakerRole().name()),
                assignSpeakerToSessionRequest.getPresentationTitle()
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Override
    public ResponseEntity<List<SessionSpeaker>> listSessionSpeakers(
            String eventCode,
            String sessionSlug
    ) {
        log.debug("Listing speakers for session {}/{}", eventCode, sessionSlug);

        Session session = findSessionBySlug(eventCode, sessionSlug);

        List<SessionSpeaker> speakers = sessionUserService.getSessionSpeakers(session.getId());

        return ResponseEntity.ok(speakers);
    }

    @Override
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<Void> removeSpeakerFromSession(
            String eventCode,
            String sessionSlug,
            String username
    ) {
        log.info("Removing speaker {} from session {}/{}", username, eventCode, sessionSlug);

        Session session = findSessionBySlug(eventCode, sessionSlug);

        sessionUserService.removeSpeakerFromSession(session.getId(), username);

        return ResponseEntity.noContent().build();
    }

    @Override
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<SessionSpeaker> confirmSpeaker(
            String eventCode,
            String sessionSlug,
            String username
    ) {
        log.info("Confirming speaker {} for session {}/{}", username, eventCode, sessionSlug);

        Session session = findSessionBySlug(eventCode, sessionSlug);

        SessionSpeaker response = sessionUserService.confirmSpeaker(session.getId(), username);

        return ResponseEntity.ok(response);
    }

    @Override
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<SessionSpeaker> declineSpeaker(
            String eventCode,
            String sessionSlug,
            String username,
            DeclineSpeakerRequest declineSpeakerRequest
    ) {
        log.info("Declining speaker {} for session {}/{}", username, eventCode, sessionSlug);

        Session session = findSessionBySlug(eventCode, sessionSlug);

        String declineReason = declineSpeakerRequest != null
                ? declineSpeakerRequest.getDeclineReason()
                : null;

        SessionSpeaker response = sessionUserService.declineSpeaker(
                session.getId(),
                username,
                declineReason
        );

        return ResponseEntity.ok(response);
    }

    /**
     * Helper method to find session by eventCode and sessionSlug.
     * Note: Since Session only stores eventId (not eventCode), we find by slug only.
     * The eventCode in the path is for API consistency but not used for lookup.
     */
    private Session findSessionBySlug(String eventCode, String sessionSlug) {
        return sessionRepository.findBySessionSlug(sessionSlug)
                .orElseThrow(() -> new EventNotFoundException(
                        "Session not found: " + sessionSlug
                ));
    }
}
