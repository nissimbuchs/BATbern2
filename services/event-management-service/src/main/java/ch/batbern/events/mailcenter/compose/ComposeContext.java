package ch.batbern.events.mailcenter.compose;

import ch.batbern.events.domain.Event;

/** What every adapter call gets: the event, the request and the organizer who sends. */
public record ComposeContext(Event event, ComposeRequest request, String organizerUsername) {
}
