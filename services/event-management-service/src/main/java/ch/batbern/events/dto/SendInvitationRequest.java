package ch.batbern.events.dto;

import ch.batbern.events.validation.ValidSpeakerIdentifier;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Request DTO for sending a speaker invitation - Story 6.1.
 *
 * Either username OR speakerPoolId must be provided (validated by @ValidSpeakerIdentifier).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ValidSpeakerIdentifier
public class SendInvitationRequest {

    /**
     * Username of the speaker to invite (ADR-003 meaningful identifier).
     * Optional if speakerPoolId is provided.
     */
    @Size(max = 100, message = "Username must be at most 100 characters")
    private String username;

    /**
     * Speaker Pool entry ID (UUID).
     * Preferred method - allows invitations to speakers without user accounts.
     * Optional if username is provided.
     */
    private UUID speakerPoolId;

    /**
     * Event code for the invitation (ADR-003 meaningful identifier).
     * Set from URL path variable - not validated as @NotBlank in request body.
     */
    @Size(max = 50, message = "Event code must be at most 50 characters")
    private String eventCode;

    /**
     * Optional personal message to include in the invitation email.
     */
    @Size(max = 2000, message = "Personal message must be at most 2000 characters")
    private String personalMessage;

    /**
     * Number of days until invitation expires (default: 14).
     */
    @Builder.Default
    private Integer expirationDays = 14;
}
