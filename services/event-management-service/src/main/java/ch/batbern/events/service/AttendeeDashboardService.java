package ch.batbern.events.service;

import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.Registration;
import ch.batbern.events.dto.AttendeeDashboardResponse;
import ch.batbern.events.dto.AttendeeEventCardResponse;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.repository.RegistrationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Builds the attendee dashboard (Story 7.6): every event the attendee participated in.
 *
 * <p>Mirrors {@code SpeakerDashboardService} — aggregates across all of the attendee's
 * (non-cancelled) registrations, batch-loads the events to avoid N+1, splits upcoming vs past by
 * event date, and sorts (upcoming soonest-first, past most-recent-first).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AttendeeDashboardService {

    private static final String CANCELLED = "cancelled";

    private final RegistrationRepository registrationRepository;
    private final EventRepository eventRepository;

    @Transactional(readOnly = true)
    public AttendeeDashboardResponse getDashboard(String username) {
        List<Registration> registrations = registrationRepository.findByAttendeeUsername(username).stream()
                .filter(r -> !CANCELLED.equalsIgnoreCase(r.getStatus()))
                .toList();

        Set<UUID> eventIds = registrations.stream()
                .map(Registration::getEventId)
                .collect(Collectors.toSet());
        Map<UUID, Event> eventsById = eventRepository.findAllById(eventIds).stream()
                .collect(Collectors.toMap(Event::getId, Function.identity()));

        Instant now = Instant.now();
        List<AttendeeEventCardResponse> upcoming = new ArrayList<>();
        List<AttendeeEventCardResponse> past = new ArrayList<>();

        for (Registration reg : registrations) {
            Event event = eventsById.get(reg.getEventId());
            if (event == null) {
                continue;
            }
            AttendeeEventCardResponse card = new AttendeeEventCardResponse(
                    event.getEventCode(),
                    event.getTitle(),
                    event.getDate(),
                    event.getVenueName(),
                    event.getWorkflowState() != null ? event.getWorkflowState().name() : null,
                    reg.getStatus());
            if (event.getDate() != null && event.getDate().isAfter(now)) {
                upcoming.add(card);
            } else {
                past.add(card);
            }
        }

        upcoming.sort(Comparator.comparing(AttendeeEventCardResponse::eventDate,
                Comparator.nullsLast(Comparator.naturalOrder())));
        past.sort(Comparator.comparing(AttendeeEventCardResponse::eventDate,
                Comparator.nullsLast(Comparator.reverseOrder())));

        String name = deriveName(registrations, username);
        log.info("Attendee dashboard for {}: {} upcoming, {} past", username, upcoming.size(), past.size());
        return new AttendeeDashboardResponse(name, upcoming, past);
    }

    /** Prefer the denormalized first+last name on a registration; fall back to the username. */
    private String deriveName(List<Registration> registrations, String username) {
        return registrations.stream()
                .filter(r -> r.getAttendeeFirstName() != null && !r.getAttendeeFirstName().isBlank())
                .map(r -> (r.getAttendeeFirstName() + " "
                        + (r.getAttendeeLastName() != null ? r.getAttendeeLastName() : "")).trim())
                .findFirst()
                .orElse(username);
    }
}
