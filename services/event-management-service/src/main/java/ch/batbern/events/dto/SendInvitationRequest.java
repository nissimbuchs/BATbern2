package ch.batbern.events.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for sending a speaker invitation - Story 6.1.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SendInvitationRequest {

    /**
     * Username of the speaker to invite (ADR-003 meaningful identifier).
     */
    @NotBlank(message = "Username is required")
    @Size(max = 100, message = "Username must be at most 100 characters")
    private String username;

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
