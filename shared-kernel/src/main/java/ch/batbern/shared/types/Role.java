package ch.batbern.companyuser.domain;

/**
 * User roles in the BATbern platform
 * Story 1.14-2: User Management Service Foundation
 * Story 1.16.2: Used in role_assignments table with UUID FK
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
