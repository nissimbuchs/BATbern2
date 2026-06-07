package ch.batbern.partners.dto;

import lombok.Builder;
import lombok.Value;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Response for GET /api/v1/partner-meetings/{id}/rsvps — Story 10.27 (AC7).
 */
@Value
@Builder
public class MeetingRsvpListResponse {
    UUID meetingId;
    Instant inviteSentAt;
    List<RsvpDTO> rsvps;
    RsvpSummary summary;
}
