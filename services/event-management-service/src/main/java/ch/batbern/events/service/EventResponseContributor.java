package ch.batbern.events.service;

import ch.batbern.events.domain.Event;
import ch.batbern.events.dto.EventResponse;

import java.util.List;
import java.util.Set;

/**
 * A module's part of an event read (event core SPI). The modules above event core (sessions,
 * speakers, registrations, topics, media) implement it in their own module, so the event read in
 * {@link EventReadService} composes them without depending on any of them (module map: a lower module
 * defines the interface, a higher one implements it; {@code AudienceAdapter} is the model).
 *
 * <p>An {@code include=} value belongs to at most one contributor: two contributors that claim the
 * same value fail the application start.
 */
public interface EventResponseContributor {

    /**
     * Which sessions a read returns. ALL: organizer surfaces. PUBLIC: by publishing state only (website
     * read model, Watch public zone). NONE: non-organizers on the organizer endpoints.
     */
    enum View { ALL, PUBLIC, NONE }

    /** The {@code include=} values this contributor expands (empty: it only enriches). */
    Set<String> includes();

    /**
     * Fields every single-event response carries, whatever the includes (registration counts, teaser
     * images). Not called by list reads.
     */
    default void enrich(Event event, EventResponse response) {
    }

    /** Expands one {@code include=} value of {@link #includes()} on a single-event response. */
    default void expand(String include, Event event, EventResponse response, View view) {
    }

    /**
     * Expands the requested values on a page of events in batch queries; {@code responses.get(i)}
     * belongs to {@code events.get(i)}. Called only for a non-empty page. {@code includes} holds every
     * requested value, also those of other contributors.
     */
    default void expandBatch(Set<String> includes, List<Event> events, List<EventResponse> responses, View view) {
    }
}
