package ch.batbern.events.dto;

import ch.batbern.shared.types.TokenAction;

import java.util.UUID;

/**
 * Result of magic link token validation.
 * Story 6.1a: Magic Link Infrastructure (AC2)
 *
 * Contains validation status and speaker context for valid tokens.
 * For invalid tokens, only valid=false and error are populated.
 *
 * @param valid true if token is valid and can be used
 * @param error error code if invalid: EXPIRED, ALREADY_USED, NOT_FOUND
 * @param speakerPoolId the speaker pool ID if valid
 * @param username the speaker's username if valid
 * @param eventCode the event code if valid (may be null if not available)
 * @param action the token action type if valid
 */
public record TokenValidationResult(
        boolean valid,
        String error,
        UUID speakerPoolId,
        String username,
        String eventCode,
        TokenAction action
) {
    /**
     * Create a valid result with speaker context.
     */
    public static TokenValidationResult valid(
            UUID speakerPoolId, String username, String eventCode, TokenAction action) {
        return new TokenValidationResult(true, null, speakerPoolId, username, eventCode, action);
    }

    /**
     * Create an expired token result.
     */
    public static TokenValidationResult expired() {
        return new TokenValidationResult(false, "EXPIRED", null, null, null, null);
    }

    /**
     * Create an already used token result.
     */
    public static TokenValidationResult alreadyUsed() {
        return new TokenValidationResult(false, "ALREADY_USED", null, null, null, null);
    }

    /**
     * Create a not found token result.
     */
    public static TokenValidationResult notFound() {
        return new TokenValidationResult(false, "NOT_FOUND", null, null, null, null);
    }
}
