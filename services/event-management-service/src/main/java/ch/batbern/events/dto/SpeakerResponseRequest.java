package ch.batbern.events.dto;

import ch.batbern.shared.types.SpeakerResponseType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for speaker invitation response.
 * Story 6.2a: Invitation Response Portal
 *
 * Used with POST /api/v1/speaker-portal/respond endpoint.
 * Token-authenticated (no session auth required).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SpeakerResponseRequest {

    /**
     * Magic link token from invitation email.
     * Base64url encoded, validated against stored hash.
     */
    @NotBlank(message = "Token is required")
    private String token;

    /**
     * Speaker's response to the invitation.
     * ACCEPT, DECLINE, or TENTATIVE
     */
    @NotNull(message = "Response is required")
    private SpeakerResponseType response;

    /**
     * Reason for decline or tentative response.
     * Required for DECLINE and TENTATIVE, ignored for ACCEPT.
     */
    private String reason;

    /**
     * Optional constraints or notes (e.g., travel, schedule).
     * Free-form text field.
     */
    private String constraints;

    /**
     * Optional preferences when accepting.
     * Contains time slot, travel requirements, tech needs, etc.
     * Only applicable when response = ACCEPT.
     */
    private SpeakerResponsePreferences preferences;
}
