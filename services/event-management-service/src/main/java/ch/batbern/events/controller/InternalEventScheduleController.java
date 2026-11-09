package ch.batbern.events.controller;

import ch.batbern.events.domain.Event;
import ch.batbern.events.exception.EventNotFoundException;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.service.EventTimeResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

/**
 * {@code GET /internal/events/{eventCode}/schedule} ({@code docs/api/internal/ems-internal.openapi.yml},
 * operation {@code getEventSchedule}): date, venue and times of an event for partner-coordination's
 * meeting invite (the BATbern event entry of the ICS).
 *
 * <p>Authorized by operation, not by the caller: it is reached inside the VPC only (the API gateway
 * denies {@code /internal/**}) and answers the same without a JWT, because partner-coordination
 * calls it for partners and from schedulers (the public event read shows sessions to organizers
 * only). The start and end are {@link EventTimeResolver}'s, the times registration mails and event
 * pages show, so the invite cannot disagree with them (17.0 deferred work, 2026-11-09: PCS used to
 * derive them itself, from a stale copy of the event type defaults). Nothing here is personal data.
 */
@RestController
@RequiredArgsConstructor
public class InternalEventScheduleController {

    private final EventRepository eventRepository;
    private final EventTimeResolver eventTimeResolver;

    @GetMapping("/internal/events/{eventCode}/schedule")
    @Transactional(readOnly = true)
    public EventSchedule schedule(@PathVariable String eventCode) {
        Event event = eventRepository.findByEventCode(eventCode)
                .orElseThrow(() -> new EventNotFoundException("Event not found: " + eventCode));
        EventTimeResolver.TimeRange times = eventTimeResolver.resolve(event);
        return new EventSchedule(event.getEventCode(), event.getTitle(), event.getDate(), event.getVenueName(),
                event.getVenueAddress(), event.getEventType() == null ? null : event.getEventType().name(),
                times.start().toInstant(), times.end().toInstant());
    }

    /** Schema {@code EventSchedule}. */
    public record EventSchedule(
            String eventCode,
            String title,
            Instant date,
            String venueName,
            String venueAddress,
            String eventType,
            Instant startTime,
            Instant endTime) {
    }
}
