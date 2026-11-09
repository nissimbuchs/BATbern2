package ch.batbern.events.controller;

import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.Session;
import ch.batbern.events.exception.EventNotFoundException;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.repository.SessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;

/**
 * {@code GET /internal/events/{eventCode}/schedule} ({@code docs/api/internal/ems-internal.openapi.yml},
 * operation {@code getEventSchedule}): date, venue and times of an event for partner-coordination's
 * meeting invite (the BATbern event entry of the ICS).
 *
 * <p>Authorized by operation, not by the caller: it is reached inside the VPC only (the API gateway
 * denies {@code /internal/**}) and answers the same without a JWT, because partner-coordination
 * calls it for partners and from schedulers. Every session with a time is listed, published or not:
 * the public {@code GET /api/v1/events/{code}?include=sessions} shows sessions to organizers only,
 * so every other caller got the default times (17.0 deferred work, 2026-11-09). Nothing here is
 * personal data.
 */
@RestController
@RequiredArgsConstructor
public class InternalEventScheduleController {

    private final EventRepository eventRepository;
    private final SessionRepository sessionRepository;

    @GetMapping("/internal/events/{eventCode}/schedule")
    @Transactional(readOnly = true)
    public EventSchedule schedule(@PathVariable String eventCode) {
        Event event = eventRepository.findByEventCode(eventCode)
                .orElseThrow(() -> new EventNotFoundException("Event not found: " + eventCode));
        List<SessionTimes> sessions = sessionRepository.findByEventId(event.getId()).stream()
                .filter(s -> s.getStartTime() != null || s.getEndTime() != null)
                .sorted(Comparator.comparing(Session::getStartTime, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(s -> new SessionTimes(s.getStartTime(), s.getEndTime()))
                .toList();
        return new EventSchedule(event.getEventCode(), event.getTitle(), event.getDate(), event.getVenueName(),
                event.getVenueAddress(), event.getEventType() == null ? null : event.getEventType().name(),
                sessions);
    }

    /** Schema {@code EventSchedule}. */
    public record EventSchedule(
            String eventCode,
            String title,
            Instant date,
            String venueName,
            String venueAddress,
            String eventType,
            List<SessionTimes> sessions) {
    }

    /** Schema {@code SessionTimes}. */
    public record SessionTimes(Instant startTime, Instant endTime) {
    }
}
