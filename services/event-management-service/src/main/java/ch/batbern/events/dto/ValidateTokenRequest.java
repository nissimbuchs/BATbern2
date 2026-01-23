package ch.batbern.events.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body for token validation endpoint.
 * Story 6.1a: Magic Link Infrastructure (AC5)
 *
 * @param token the base64url-encoded magic link token
 */
public record ValidateTokenRequest(
        @NotBlank(message = "Token is required")
        String token
) {
}
