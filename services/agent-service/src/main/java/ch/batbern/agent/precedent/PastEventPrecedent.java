package ch.batbern.agent.precedent;

import java.util.Objects;

/**
 * A precedent from the history of past events (story 6, AD-8), read through {@code findPastEvents}
 * on the public API with the run's token.
 *
 * @param sourceId  the citeable source: {@code event:<eventCode>}
 * @param eventCode the ADR-003 id of the event
 * @param title     its title, as the API answered; null when it sent none
 * @param date      its date, as the API answered; null when it sent none
 */
public record PastEventPrecedent(String sourceId, String eventCode, String title, String date) {

    /** Source prefix of a past event. */
    public static final String EVENTS = "event:";

    public PastEventPrecedent {
        Objects.requireNonNull(sourceId, "sourceId");
        Objects.requireNonNull(eventCode, "eventCode");
    }

    /** The precedent of the event {@code eventCode}. */
    public static PastEventPrecedent of(String eventCode, String title, String date) {
        return new PastEventPrecedent(EVENTS + eventCode, eventCode, title, date);
    }
}
