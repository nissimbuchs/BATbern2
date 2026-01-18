package ch.batbern.events.dto;

import ch.batbern.events.domain.ResponseType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for speaker response to invitation - Story 6.1, extended in Story 6.2.
 *
 * Used when speaker responds via the unique response token URL.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RespondToInvitationRequest {

    /**
     * Speaker's response to the invitation.
     */
    @NotNull(message = "Response type is required")
    private ResponseType responseType;

    /**
     * Optional reason for declining (required if responseType is DECLINED).
     */
    @Size(max = 2000, message = "Decline reason must be at most 2000 characters")
    private String declineReason;

    /**
     * Optional notes or preferences from the speaker.
     */
    @Size(max = 2000, message = "Notes must be at most 2000 characters")
    private String notes;

    /**
     * Speaker preferences when accepting invitation - Story 6.2.
     * Optional: only relevant when responseType is ACCEPTED.
     */
    @Valid
    private SpeakerResponsePreferences preferences;
}
