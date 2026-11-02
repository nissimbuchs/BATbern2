package ch.batbern.events.mailcenter.compose;

import java.time.Instant;

/**
 * Own text to registrants with a subject that already went to the event (Story 16.5 AC9, 409); the
 * organizer confirms with {@code confirmResend}.
 */
public class AlreadySentException extends RuntimeException {

    private final Instant sentAt;

    public AlreadySentException(String subject, Instant sentAt) {
        super("A mail with the subject '" + subject + "' was already sent to this event's registrants on " + sentAt);
        this.sentAt = sentAt;
    }

    public Instant getSentAt() {
        return sentAt;
    }
}
