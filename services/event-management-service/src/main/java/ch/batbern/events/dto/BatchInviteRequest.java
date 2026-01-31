package ch.batbern.events.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Request DTO for batch speaker invitations.
 * Story 6.1b: Speaker Invitation System (AC5)
 *
 * @param speakers List of speakers to invite (1-50 speakers per batch)
 */
public record BatchInviteRequest(
        @NotEmpty(message = "At least one speaker must be provided")
        @Size(max = 50, message = "Maximum 50 speakers per batch")
        @Valid
        List<InviteSpeakerRequest> speakers
) {
}
