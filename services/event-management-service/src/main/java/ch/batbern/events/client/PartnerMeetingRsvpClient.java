package ch.batbern.events.client;

import java.util.UUID;

/**
 * Client for recording iCal RSVP responses to the partner-coordination-service — Story 10.27 (AC4).
 *
 * Calls POST /internal/partner-meetings/rsvps without JWT (SQS async context has no SecurityContext).
 */
public interface PartnerMeetingRsvpClient {

    /**
     * Record or update an RSVP response for a partner meeting.
     *
     * @param meetingId      partner meeting UUID
     * @param attendeeEmail  respondent email
     * @param partStat       iCal PARTSTAT value: ACCEPTED, DECLINED, or TENTATIVE
     */
    void recordRsvp(UUID meetingId, String attendeeEmail, String partStat);
}
