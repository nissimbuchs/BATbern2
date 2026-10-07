package ch.batbern.events.service;

import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.Session;
import ch.batbern.events.entity.AgendaConfig;
import ch.batbern.events.repository.SessionRepository;
import ch.batbern.shared.exception.NotFoundException;
import ch.batbern.shared.types.EventWorkflowState;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;

/**
 * Resolves event start/end times with cascading priority:
 * 1. Session times (earliest start / latest end) — only when agenda is published
 * 2. Agenda config: the per-event override (Story 15.2) if present, else the event-type template,
 *    resolved by {@link AgendaConfigResolver} exactly as the slot grid does
 * 3. Fallback: the event type's conventional start ({@link EventTypeDefaults}) + 3 hours
 *
 * Shared by RegistrationEmailService and event API responses.
 */
@Service
@RequiredArgsConstructor
public class EventTimeResolver {

    private static final ZoneId SWISS_ZONE = ZoneId.of("Europe/Zurich");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    private static final Set<EventWorkflowState> AGENDA_FINALIZED_STATES = Set.of(
            EventWorkflowState.AGENDA_PUBLISHED,
            EventWorkflowState.EVENT_LIVE,
            EventWorkflowState.EVENT_COMPLETED,
            EventWorkflowState.ARCHIVED
    );

    private final AgendaConfigResolver agendaConfigResolver;
    private final SessionRepository sessionRepository;

    public record TimeRange(ZonedDateTime start, ZonedDateTime end) {}

    /**
     * Resolve event start/end times.
     */
    public TimeRange resolve(Event event) {
        LocalDate eventDate = event.getDate().atZone(SWISS_ZONE).toLocalDate();

        // Priority 1: Session times when agenda is finalized
        if (event.getWorkflowState() != null && AGENDA_FINALIZED_STATES.contains(event.getWorkflowState())) {
            List<Session> sessions = sessionRepository.findByEventIdAndStartTimeIsNotNull(event.getId());
            if (!sessions.isEmpty()) {
                Instant earliestStart = sessions.stream()
                        .map(Session::getStartTime)
                        .min(Instant::compareTo)
                        .get();
                Instant latestEnd = sessions.stream()
                        .filter(s -> s.getEndTime() != null)
                        .map(Session::getEndTime)
                        .max(Instant::compareTo)
                        .orElse(earliestStart.plusSeconds(4 * 3600));
                return new TimeRange(
                        earliestStart.atZone(SWISS_ZONE),
                        latestEnd.atZone(SWISS_ZONE)
                );
            }
        }

        // Priority 2: agenda config (per-event override, else template), same source as the slot grid
        AgendaConfig config = resolveConfig(event);
        if (config != null && config.getTypicalStartTime() != null) {
            ZonedDateTime start = eventDate.atTime(config.getTypicalStartTime()).atZone(SWISS_ZONE);
            ZonedDateTime end = config.getTypicalEndTime() != null
                    ? eventDate.atTime(config.getTypicalEndTime()).atZone(SWISS_ZONE)
                    : start.plusHours(4);
            return new TimeRange(start, end);
        }

        // Priority 3: Fallback — the event type's conventional start time
        ZonedDateTime start = eventDate.atTime(EventTypeDefaults.startTime(event.getEventType()))
                .atZone(SWISS_ZONE);
        return new TimeRange(start, start.plusHours(3));
    }

    private AgendaConfig resolveConfig(Event event) {
        try {
            return agendaConfigResolver.resolve(event);
        } catch (NotFoundException e) {
            return null;
        }
    }

    /**
     * Format start time as HH:mm string.
     */
    public String formatStartTime(Event event) {
        return resolve(event).start().format(TIME_FORMATTER);
    }

    /**
     * Format end time as HH:mm string.
     */
    public String formatEndTime(Event event) {
        return resolve(event).end().format(TIME_FORMATTER);
    }
}
