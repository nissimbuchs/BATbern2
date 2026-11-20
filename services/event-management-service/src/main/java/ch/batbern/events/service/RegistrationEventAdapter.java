package ch.batbern.events.service;

import ch.batbern.events.domain.Event;
import ch.batbern.events.dto.EventResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The registrations module's part of event reads and writes: the live counts every single-event read
 * carries (Story 10.11), {@code include=registrations}, the waitlist fill when the capacity is saved
 * and the delete guard (an event with real attendees answers 409). Moved from {@code EventController}
 * unchanged.
 */
@Component
@Order(1)
@RequiredArgsConstructor
public class RegistrationEventAdapter implements EventResponseContributor, EventWriteHooks {

    private final RegistrationService registrationService;
    private final WaitlistPromotionService waitlistPromotionService;

    @Override
    public Set<String> includes() {
        return Set.of("registrations");
    }

    /**
     * Live registration counts: confirmedCount, waitlistCount, realAttendeeCount and spotsRemaining
     * (null when the capacity is unlimited). 3 DB queries, acceptable for single-event endpoints.
     */
    @Override
    public void enrich(Event event, EventResponse response) {
        var counts = registrationService.countsFor(event.getId());
        response.setConfirmedCount((int) counts.confirmed());
        response.setWaitlistCount((int) counts.waitlisted());
        // Real attendees exclude programmatic enrollments (organizers/partners/speakers); drives
        // the organizer Delete-button enable-state and mirrors the deleteEvent 409 guard.
        response.setRealAttendeeCount((int) counts.realAttendees());
        if (response.getRegistrationCapacity() != null) {
            response.setSpotsRemaining((int) (response.getRegistrationCapacity() - counts.confirmed()));
        }
    }

    @Override
    public void expand(String include, Event event, EventResponse response, View view) {
        response.setCurrentAttendeeCount(
                registrationService.activeCounts(List.of(event.getId())).get(event.getId()).intValue());
    }

    @Override
    public void expandBatch(Set<String> includes, List<Event> events, List<EventResponse> responses, View view) {
        if (!includes.contains("registrations")) {
            return;
        }
        // One grouped query for the page
        Map<UUID, Long> activeCounts = registrationService.activeCounts(
                events.stream().map(Event::getId).toList());
        for (int i = 0; i < events.size(); i++) {
            responses.get(i).setCurrentAttendeeCount(activeCounts.get(events.get(i).getId()).intValue());
        }
    }

    /** A raised capacity frees places: give them to the waitlist, in order. */
    @Override
    public void capacitySaved(Event event) {
        waitlistPromotionService.promoteFromWaitlist(event.getId());
    }

    /**
     * Only real (self-registered) attendees block the delete. Programmatic registrations (organizers
     * and partners auto-enrolled at creation, auto-registered speakers) never do, otherwise no event
     * would ever be deletable; the delete cascades them. Real attendees: 409, cancel the event instead.
     */
    @Override
    public void beforeDelete(Event event) {
        registrationService.requireNoRealAttendees(event.getEventCode(), event.getId());
    }

    /** #824: a cancelled event's registrations end, and their holders are told by mail. */
    @Override
    public void cancelled(Event event) {
        registrationService.cancelForCancelledEvent(event);
    }
}
