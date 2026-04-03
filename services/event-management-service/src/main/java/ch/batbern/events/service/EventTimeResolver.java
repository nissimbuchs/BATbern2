package ch.batbern.events.service;

import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.Session;
import ch.batbern.events.entity.EventTypeConfiguration;
import ch.batbern.events.repository.EventTypeRepository;
import ch.batbern.events.repository.SessionRepository;
import ch.batbern.shared.types.EventWorkflowState;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Resolves event start/end times with cascading priority:
 * 1. Session times (earliest start / latest end) — only when agenda is published
 * 2. EventTypeConfiguration (typicalStartTime / typicalEndTime)
 * 3. Fallback: 16:00 Swiss time + 3 hours (standard BATbern event start)
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

    private final EventTypeRepository eventTypeRepository;
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

        // Priority 2: Event type configuration
        if (event.getEventType() != null) {
            Optional<EventTypeConfiguration> config = eventTypeRepository.findByType(event.getEventType());
            if (config.isPresent()) {
                LocalTime startTime = config.get().getTypicalStartTime();
                LocalTime endTime = config.get().getTypicalEndTime();
                if (startTime != null) {
                    ZonedDateTime start = eventDate.atTime(startTime).atZone(SWISS_ZONE);
                    ZonedDateTime end = endTime != null
                            ? eventDate.atTime(endTime).atZone(SWISS_ZONE)
                            : start.plusHours(4);
                    return new TimeRange(start, end);
                }
            }
        }

        // Priority 3: Fallback — 16:00 Swiss time (standard BATbern event start)
        ZonedDateTime start = eventDate.atTime(LocalTime.of(16, 0)).atZone(SWISS_ZONE);
        return new TimeRange(start, start.plusHours(3));
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
