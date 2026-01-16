package ch.batbern.events.domain;

/**
 * Speaker availability status - Story 6.0.
 *
 * Enum Value Flow (per coding-standards.md):
 * - Java/JSON/API: AVAILABLE (UPPER_CASE)
 * - Database: 'available' (lowercase via AttributeConverter)
 */
public enum SpeakerAvailability {
    /**
     * Speaker is available for new engagements.
     */
    AVAILABLE,

    /**
     * Speaker is currently busy but may be available later.
     */
    BUSY,

    /**
     * Speaker is not available for engagements.
     */
    UNAVAILABLE
}
