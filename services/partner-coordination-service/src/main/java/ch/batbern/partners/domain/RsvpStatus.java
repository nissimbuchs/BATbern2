package ch.batbern.partners.domain;

/**
 * iCal PARTSTAT values for partner meeting RSVP responses — Story 10.27.
 * Maps to the CHECK constraint in partner_meeting_rsvps.status.
 */
public enum RsvpStatus {
    ACCEPTED,
    DECLINED,
    TENTATIVE
}
