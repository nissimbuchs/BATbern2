package ch.batbern.partners.dto;

import lombok.Builder;
import lombok.Value;

import java.util.UUID;

/**
 * Response for POST /partner-meetings/{meetingId}/send-invite.
 *
 * Returns 202 Accepted — invite is dispatched asynchronously (AC8).
 */
@Value
@Builder
public class SendInviteResponse {

    String message;
    UUID meetingId;
    int recipientCount;
}
