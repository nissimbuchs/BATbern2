package ch.batbern.events.watch;

import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.Session;
import ch.batbern.events.domain.SessionUser;
import ch.batbern.events.domain.Speaker;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.repository.SessionRepository;
import ch.batbern.events.repository.SpeakerRepository;

import ch.batbern.events.watch.dto.ActiveEventDetail;
import ch.batbern.events.watch.dto.ActiveEventsResponse;
import ch.batbern.events.watch.dto.SessionDetail;
import ch.batbern.events.watch.dto.SpeakerDetail;
import ch.batbern.shared.types.EventWorkflowState;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Watch organizer event endpoint.
 * W2.3: Event Join & Schedule Sync — full schedule sync for paired organizers.
 *
 * GET /api/v1/watch/organizers/me/active-events
 * Requires: JWT with ROLE_ORGANIZER
 */
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
            EventWorkflowState.AGENDA_FINALIZED,
            EventWorkflowState.EVENT_LIVE
    );

    private final EventRepository eventRepository;
    private final SessionRepository sessionRepository;
    private final SpeakerRepository speakerRepository;

    /**
     * Returns active events assigned to the authenticated organizer.
     * Events within ±3 days with AGENDA_PUBLISHED, AGENDA_FINALIZED, or EVENT_LIVE state.
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

        List<Event> activeEvents = eventRepository.findActiveEventsForOrganizer(
                startDate, endDate, ACTIVE_STATES);

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

        List<SessionDetail> sessionDetails = sessions.stream()
                .map(this::mapToSessionDetail)
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

    private SessionDetail mapToSessionDetail(Session session) {
        List<SpeakerDetail> speakerDetails = session.getSessionUsers().stream()
                .map(this::mapToSpeakerDetail)
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
                null,   // actualStartTime — W4 implements session control
                null,   // actualEndTime
                0,      // overrunMinutes
                null    // completedBy
        );
    }

    private SpeakerDetail mapToSpeakerDetail(SessionUser sessionUser) {
        Optional<Speaker> speakerOpt = speakerRepository.findByUsername(sessionUser.getUsername());

        String firstName = speakerOpt.map(Speaker::getFirstName)
                .orElse(sessionUser.getSpeakerFirstName());
        String lastName = speakerOpt.map(Speaker::getLastName)
                .orElse(sessionUser.getSpeakerLastName());
        String bio = speakerOpt.map(Speaker::getBio).orElse(null);
        String profilePictureUrl = speakerOpt.map(Speaker::getProfilePictureUrl).orElse(null);

        return new SpeakerDetail(
                sessionUser.getUsername(),
                firstName,
                lastName,
                null,           // company — not in Speaker entity, cross-service call deferred
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
}
