package ch.batbern.partners.domain;

/**
 * Enumeration of possible statuses for topic suggestions submitted by partners.
 *
 * Tracks the lifecycle of a suggestion from initial submission through review
 * to final disposition (acceptance or rejection) and potential implementation.
 *
 * <p><b>Database:</b> Stored as UPPERCASE (SUBMITTED, UNDER_REVIEW, etc.)</p>
 * <p><b>API:</b> Serialized as lowercase (submitted, under_review, etc.) via controller mapping</p>
 */
public enum SuggestionStatus {
    /**
     * Initial state when partner submits a topic suggestion.
     */
    SUBMITTED,

    /**
     * Suggestion is being actively reviewed by organizers.
     */
    UNDER_REVIEW,

    /**
     * Suggestion has been accepted and will be scheduled for a future event.
     */
    ACCEPTED,

    /**
     * Suggestion has been reviewed but declined (not suitable for event topics).
     */
    REJECTED,

    /**
     * Accepted suggestion has been implemented as an event topic.
     */
    IMPLEMENTED
}
