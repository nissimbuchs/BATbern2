package ch.batbern.events.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Story 7.3 — response for a dedicated "slides are online" send.
 *
 * <p>Deliberately separate from {@code NewsletterSendResponse}: the slides-online send is a
 * sibling of the subscriber newsletter, not the same flow. The send runs asynchronously, so
 * this is returned immediately after the audit row is created (status {@code PENDING}); the
 * organizer can poll the shared newsletter send-status endpoint with the returned id.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SlidesOnlineSendResponse {

    /** Id of the underlying {@code newsletter_sends} audit row (template_key='slides-online'). */
    private UUID sendId;

    /** Send-job lifecycle status at return time (PENDING). */
    private String status;

    /** Number of active registrants resolved as the recipient base for this send. */
    private int recipientCount;
}
