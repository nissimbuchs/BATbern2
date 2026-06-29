package ch.batbern.events.service;

import ch.batbern.events.client.UserApiClient;
import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.Registration;
import ch.batbern.events.core.dto.generated.AttendeeDashboardResponse;
import ch.batbern.events.core.dto.generated.AttendeeEventCardResponse;
import ch.batbern.events.dto.generated.users.UserResponse;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.repository.RegistrationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneOffset;
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
    private final UserApiClient userApiClient;

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
            AttendeeEventCardResponse card = AttendeeEventCardResponse.builder()
                    .eventCode(event.getEventCode())
                    .eventTitle(event.getTitle())
                    .eventDate(event.getDate() != null ? event.getDate().atOffset(ZoneOffset.UTC) : null)
                    .eventLocation(event.getVenueName())
                    .workflowState(event.getWorkflowState() != null ? event.getWorkflowState().name() : null)
                    .registrationStatus(reg.getStatus())
                    .build();
            if (event.getDate() != null && event.getDate().isAfter(now)) {
                upcoming.add(card);
            } else {
                past.add(card);
            }
        }

        upcoming.sort(Comparator.comparing(AttendeeEventCardResponse::getEventDate,
                Comparator.nullsLast(Comparator.naturalOrder())));
        past.sort(Comparator.comparing(AttendeeEventCardResponse::getEventDate,
                Comparator.nullsLast(Comparator.reverseOrder())));

        String name = resolveGreetingName(registrations, username);
        log.info("Attendee dashboard for {}: {} upcoming, {} past", username, upcoming.size(), past.size());
        return AttendeeDashboardResponse.builder()
                .attendeeName(name)
                .upcomingEvents(upcoming)
                .pastEvents(past)
                .build();
    }

    /**
     * The first name to greet the attendee with. Primary source is the CUMS profile (the
     * authoritative identity, like SpeakerDashboardService); falls back to a registration's
     * denormalized first name, then — only if all else fails — the raw username. Greeting with the
     * login username was the bug: profile lookup makes "Welcome, alice.muller" → "Welcome, Alice".
     */
    private String resolveGreetingName(List<Registration> registrations, String username) {
        try {
            UserResponse profile = userApiClient.getUserByUsername(username);
            if (profile != null && profile.getFirstName() != null && !profile.getFirstName().isBlank()) {
                return profile.getFirstName().trim();
            }
        } catch (Exception e) {
            log.debug("CUMS profile lookup failed for {} on attendee dashboard — falling back: {}",
                    username, e.getMessage());
        }
        return registrations.stream()
                .map(Registration::getAttendeeFirstName)
                .filter(n -> n != null && !n.isBlank())
                .map(String::trim)
                .findFirst()
                .orElse(username);
    }
}
