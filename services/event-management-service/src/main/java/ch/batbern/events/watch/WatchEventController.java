package ch.batbern.events.watch;

import ch.batbern.events.client.UserApiClient;
import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.Session;
import ch.batbern.events.domain.SessionUser;
import ch.batbern.events.dto.generated.users.UserResponse;
import ch.batbern.events.exception.UserNotFoundException;
import ch.batbern.events.exception.UserServiceException;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.repository.SessionRepository;

import ch.batbern.events.watch.dto.ActiveEventDetail;
import ch.batbern.events.watch.dto.ActiveEventsResponse;
import ch.batbern.events.watch.dto.ArrivalStatusListDto;
import ch.batbern.events.watch.dto.ConfirmArrivalRequest;
import ch.batbern.events.watch.dto.SessionDetail;
import ch.batbern.events.watch.dto.SpeakerArrivalBroadcast;
import ch.batbern.events.watch.dto.SpeakerDetail;
import ch.batbern.shared.types.EventWorkflowState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Watch organizer event endpoint.
 * W2.3: Event Join & Schedule Sync — full schedule sync for paired organizers.
 *
 * GET /api/v1/watch/organizers/me/active-events
 * Requires: JWT with ROLE_ORGANIZER
 *
 * Story 11.C.1: speaker enrichment (first/last/bio/profile_picture_url) is now
 * resolved via UserApiClient (per-record HTTP enrichment per ADR-004) instead
 * of the deleted Speaker entity. N round-trips are acceptable at this scale —
 * an event detail loads 5-10 speakers and the UserApiClient cache (15-min TTL)
 * absorbs repeats.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/watch")
@RequiredArgsConstructor
public class WatchEventController {

    private static final ZoneId ZURICH_ZONE = ZoneId.of("Europe/Zurich");
    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZURICH_ZONE);
    private static final DateTimeFormatter TIME_FORMATTER =
            DateTimeFormatter.ofPattern("HH:mm").withZone(ZURICH_ZONE);
    private static final DateTimeFormatter ISO_FORMATTER =
            DateTimeFormatter.ISO_INSTANT;

    /** Active workflow states for Watch organizer access (W2.3: AC#1, AC#4, AC#5) */
    private static final List<EventWorkflowState> ACTIVE_STATES = List.of(
            EventWorkflowState.AGENDA_PUBLISHED,
            EventWorkflowState.EVENT_LIVE
    );

    private final EventRepository eventRepository;
    private final SessionRepository sessionRepository;
    private final UserApiClient userApiClient;
    private final WatchSpeakerArrivalService arrivalService;

    /**
     * Returns active events assigned to the authenticated organizer.
     * Events within ±3 days with AGENDA_PUBLISHED or EVENT_LIVE state.
     * AC#1: Full schedule sync; AC#4: No active event; AC#5: Event preview >1h away.
     */
    @GetMapping("/organizers/me/active-events")
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<ActiveEventsResponse> getActiveEvents() {
        Instant now = Instant.now();
        Instant startDate = now.minus(3, ChronoUnit.DAYS).truncatedTo(ChronoUnit.DAYS);
        Instant endDate = now.plus(3, ChronoUnit.DAYS)
                .truncatedTo(ChronoUnit.DAYS)
                .plus(1, ChronoUnit.DAYS);  // Include entire end day

        List<Event> activeEvents = eventRepository.findActiveEvents(startDate, endDate, ACTIVE_STATES);

        List<ActiveEventDetail> eventDetails = activeEvents.stream()
                .map(this::mapToActiveEventDetail)
                .collect(Collectors.toList());

        return ResponseEntity.ok(new ActiveEventsResponse(eventDetails));
    }

    private ActiveEventDetail mapToActiveEventDetail(Event event) {
        List<Session> sessions = sessionRepository.findByEventCode(event.getEventCode());

        // Sort sessions by startTime
        sessions.sort(Comparator.comparing(
                s -> s.getStartTime() != null ? s.getStartTime() : Instant.MIN));

        // Resolve speaker identity via per-record UserApiClient lookup (Story 11.C.1).
        // The UserApiClient implementation caches results (15-min TTL), so repeats
        // across sessions are cheap. N round-trips are acceptable for 5-10 speakers per event.
        Set<String> speakerUsernames = sessions.stream()
                .flatMap(s -> s.getSessionUsers().stream())
                .map(SessionUser::getUsername)
                .collect(Collectors.toSet());
        Map<String, UserResponse> userMap = new HashMap<>();
        boolean userServiceDegraded = false;
        for (String username : speakerUsernames) {
            try {
                userMap.put(username, userApiClient.getUserByUsername(username));
            } catch (UserNotFoundException e) {
                log.debug("Speaker user {} not found; falling back to session_users cache", username);
            } catch (UserServiceException e) {
                // user-management-service degraded — fall back to local session_users cache
                // for all remaining usernames in this event detail load. Log once per request
                // to avoid log spam.
                if (!userServiceDegraded) {
                    log.warn("user-management-service degraded for event {}: {}; using session_users cache fallback",
                            event.getEventCode(), e.getMessage());
                    userServiceDegraded = true;
                }
            }
        }

        List<SessionDetail> sessionDetails = sessions.stream()
                .map(session -> mapToSessionDetail(session, userMap))
                .collect(Collectors.toList());

        // Derive typical start/end time from sessions (HH:mm in Europe/Zurich)
        String typicalStartTime = sessions.stream()
                .filter(s -> s.getStartTime() != null)
                .findFirst()
                .map(s -> TIME_FORMATTER.format(s.getStartTime()))
                .orElse(null);

        String typicalEndTime = sessions.stream()
                .filter(s -> s.getEndTime() != null)
                .reduce((first, second) -> second)  // last element
                .map(s -> TIME_FORMATTER.format(s.getEndTime()))
                .orElse(null);

        String eventStatus = determineEventStatus(event);
        String eventDate = DATE_FORMATTER.format(event.getDate());

        return new ActiveEventDetail(
                event.getEventCode(),
                event.getTitle(),
                eventDate,
                event.getVenueName(),
                typicalStartTime,
                typicalEndTime,
                event.getThemeImageUrl(),
                event.getCurrentPublishedPhase(),
                eventStatus,
                sessionDetails
        );
    }

    private SessionDetail mapToSessionDetail(Session session, Map<String, UserResponse> userMap) {
        List<SpeakerDetail> speakerDetails = session.getSessionUsers().stream()
                .map(su -> mapToSpeakerDetail(su, userMap))
                .collect(Collectors.toList());

        String scheduledStart = session.getStartTime() != null
                ? ISO_FORMATTER.format(session.getStartTime()) : null;
        String scheduledEnd = session.getEndTime() != null
                ? ISO_FORMATTER.format(session.getEndTime()) : null;

        Integer durationMinutes = null;
        if (session.getStartTime() != null && session.getEndTime() != null) {
            durationMinutes = (int) ChronoUnit.MINUTES.between(
                    session.getStartTime(), session.getEndTime());
        }

        // Derive session status from timing (session status fields deferred to W4)
        String status = deriveSessionStatus(session);

        String actualStart = session.getActualStartTime() != null
                ? ISO_FORMATTER.format(session.getActualStartTime()) : null;
        String actualEnd = session.getActualEndTime() != null
                ? ISO_FORMATTER.format(session.getActualEndTime()) : null;
        int overrun = session.getOverrunMinutes() != null ? session.getOverrunMinutes() : 0;

        return new SessionDetail(
                session.getSessionSlug(),
                session.getTitle(),
                session.getDescription(),
                session.getSessionType(),
                scheduledStart,
                scheduledEnd,
                durationMinutes,
                speakerDetails,
                status,
                actualStart,
                actualEnd,
                overrun,
                session.getCompletedByUsername()
        );
    }

    private SpeakerDetail mapToSpeakerDetail(SessionUser sessionUser, Map<String, UserResponse> userMap) {
        UserResponse user = userMap.get(sessionUser.getUsername());

        String firstName = user != null ? user.getFirstName() : sessionUser.getSpeakerFirstName();
        String lastName = user != null ? user.getLastName() : sessionUser.getSpeakerLastName();
        String bio = user != null ? user.getBio() : null;
        String profilePictureUrl = user != null && user.getProfilePictureUrl() != null
                ? user.getProfilePictureUrl().toString() : null;

        return new SpeakerDetail(
                sessionUser.getUsername(),
                firstName,
                lastName,
                null,           // company — not in UserResponse, cross-service call deferred
                null,           // companyLogoUrl — cross-service call deferred
                profilePictureUrl,
                bio,
                sessionUser.getSpeakerRole() != null
                        ? sessionUser.getSpeakerRole().name().toLowerCase() : null
        );
    }

    /**
     * Derives event status from workflow state.
     * EVENT_LIVE → "LIVE", EVENT_COMPLETED/ARCHIVED → "COMPLETED", else → "SCHEDULED".
     */
    private String determineEventStatus(Event event) {
        return switch (event.getWorkflowState()) {
            case EVENT_LIVE -> "LIVE";
            case EVENT_COMPLETED, ARCHIVED -> "COMPLETED";
            default -> "SCHEDULED";
        };
    }

    /**
     * Derives session status from timing relative to now.
     * Before startTime → SCHEDULED, between start/end → ACTIVE, after end → COMPLETED.
     */
    private String deriveSessionStatus(Session session) {
        if (session.getStartTime() == null) {
            return "SCHEDULED";
        }
        Instant now = Instant.now();
        if (now.isBefore(session.getStartTime())) {
            return "SCHEDULED";
        }
        if (session.getEndTime() != null && now.isAfter(session.getEndTime())) {
            return "COMPLETED";
        }
        return "ACTIVE";
    }

    // W2.4: Speaker Arrival Tracking Endpoints

    /**
     * Returns all arrival confirmations for an event.
     * Used by Watch clients on initial load (REST fallback / initial state fetch).
     * GET /api/v1/watch/events/{eventCode}/arrivals
     */
    @GetMapping("/events/{eventCode}/arrivals")
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<ArrivalStatusListDto> getArrivals(
            @PathVariable String eventCode
    ) {
        return ResponseEntity.ok(new ArrivalStatusListDto(arrivalService.getArrivals(eventCode)));
    }

    /**
     * Confirms a speaker's arrival (REST fallback when WebSocket is offline).
     * Idempotent: confirming an already-arrived speaker is a no-op.
     * POST /api/v1/watch/events/{eventCode}/arrivals
     */
    @PostMapping("/events/{eventCode}/arrivals")
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<SpeakerArrivalBroadcast> confirmArrival(
            @PathVariable String eventCode,
            @RequestBody ConfirmArrivalRequest request,
            Authentication authentication
    ) {
        SpeakerArrivalBroadcast result = arrivalService.confirmArrival(
                eventCode,
                request.speakerUsername(),
                authentication.getName()
        );
        return ResponseEntity.status(201).body(result);
    }
}
