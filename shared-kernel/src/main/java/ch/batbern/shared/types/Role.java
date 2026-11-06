package ch.batbern.shared.types;

/**
 * User roles in the BATbern platform.
 * Story 1.14-2: User Management Service Foundation.
 * Story 1.16.2: Used in role_assignments table with UUID FK (stored by name, UPPER_CASE).
 * #1236: moved from CUMS to the shared kernel so EMS can use it (name badges). The declaration
 * order is the precedence used where one person holds several roles: ORGANIZER first, ATTENDEE last.
 */
public enum Role {
    /**
     * Event organizer - can manage events, speakers, partners
     */
    ORGANIZER,

    /**
     * Speaker - presents at events
     */
    SPEAKER,

    /**
     * Partner/Sponsor - supports events financially or through services
     */
    PARTNER,

    /**
     * Attendee - participates in events
     */
    ATTENDEE
}
