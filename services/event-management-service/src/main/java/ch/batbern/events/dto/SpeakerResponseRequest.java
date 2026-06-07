package ch.batbern.events.dto;

import ch.batbern.shared.types.SpeakerResponseType;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for speaker invitation response.
 * Story 6.2a: Invitation Response Portal
 * Story 11.E.3 (ADR-009): Cognito Bearer auth replaces magic-link token; the eventCode now
 * arrives as a path parameter on the controller, so it is not part of this body.
 *
 * <p>Used with {@code POST /api/v1/speaker-portal/events/{eventCode}/respond}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SpeakerResponseRequest {

    /**
     * Speaker's response to the invitation.
     * ACCEPT or DECLINE (TENTATIVE was removed in Story 11.B.1).
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
