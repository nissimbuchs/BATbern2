package ch.batbern.events.exception;

/**
 * Story 7.2 "I Could Speak on That": thrown when an attendee tries to self-nominate for an
 * event whose topic is not yet set OR which is not yet published.
 *
 * <p>Self-nomination only opens once the next event's direction is public — i.e.
 * {@code event.topicCode != null} AND the event is published
 * ({@code publishedAt != null} or {@code currentPublishedPhase != 'none'}). Calling before
 * then is rejected as HTTP 409 Conflict by {@code GlobalExceptionHandler} with body
 * {@code details.code = "SELF_NOMINATION_NOT_ALLOWED"}; no pool row is created (AC4).
 */
public class SelfNominationNotAllowedException extends RuntimeException {

    public SelfNominationNotAllowedException(String message) {
        super(message);
    }
}
