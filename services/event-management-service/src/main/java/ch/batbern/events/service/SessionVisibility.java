package ch.batbern.events.service;

import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.Session;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The public view of an event's sessions, by publishing state only, whoever asks (sessions module).
 * Implemented above, in publishing ({@code PublicSessionVisibilityService}), so the session
 * expansion of an event read can ask for it without depending upward (module map: a lower module
 * defines the interface, a higher one implements it).
 */
public interface SessionVisibility {

    /** The sessions of {@code event} the public website may show. */
    List<Session> publicSessions(Event event, List<Session> sessions);

    /** Batch variant for list reads: every given event mapped to its public sessions. */
    Map<UUID, List<Session>> publicSessions(Collection<Event> events, Map<UUID, List<Session>> sessionsByEventId);
}
