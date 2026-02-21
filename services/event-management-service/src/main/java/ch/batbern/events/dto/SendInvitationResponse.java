package ch.batbern.events.dto;

import ch.batbern.shared.types.SpeakerWorkflowState;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Response DTO for sending an invitation email.
 * Story 6.1b: Speaker Invitation System (AC3)
 *
 * @param speakerPoolId The ID of the SpeakerPool entry
 * @param username The speaker's username
 * @param email The speaker's email address
 * @param status The updated workflow status (should be INVITED)
 * @param invitedAt When the invitation was sent
 * @param responseDeadline The deadline for the speaker to respond
 * @param contentDeadline The deadline for the speaker to submit content (optional)
 */
public record SendInvitationResponse(
        UUID speakerPoolId,
        String username,
        String email,
        SpeakerWorkflowState status,
        Instant invitedAt,
        LocalDate responseDeadline,
        LocalDate contentDeadline
) {
}
