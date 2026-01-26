package ch.batbern.events.dto;

import ch.batbern.shared.types.TokenAction;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.UUID;

/**
 * Result of magic link token validation.
 * Story 6.1a: Magic Link Infrastructure (AC2)
 * Story 6.2a: Enhanced for frontend speaker portal display
 *
 * Contains validation status and speaker context for valid tokens.
 * For invalid tokens, only valid=false and error are populated.
 *
 * @param valid true if token is valid and can be used
 * @param error error code if invalid: EXPIRED, ALREADY_USED, NOT_FOUND
 * @param speakerPoolId the speaker pool ID if valid
 * @param username the speaker's username if valid
 * @param speakerName the speaker's display name (firstName lastName)
 * @param eventCode the event code if valid
 * @param eventTitle the event title for display
 * @param eventDate the event date formatted for display (dd.MM.yyyy)
 * @param sessionTitle the session title if speaker is assigned to a session
 * @param responseDeadline the response deadline formatted (dd.MM.yyyy)
 * @param invitationMessage optional message from organizers
 * @param alreadyResponded true if speaker has already responded
 * @param previousResponse the previous response type if already responded
 * @param previousResponseDate when the previous response was submitted
 * @param action the token action type if valid
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TokenValidationResult(
        boolean valid,
        String error,
        UUID speakerPoolId,
        String username,
        String speakerName,
        String eventCode,
        String eventTitle,
        String eventDate,
        String sessionTitle,
        String responseDeadline,
        String invitationMessage,
        boolean alreadyResponded,
        String previousResponse,
        Instant previousResponseDate,
        TokenAction action
) {
    /**
     * Create a valid result with full speaker context.
     */
    public static TokenValidationResult valid(
            UUID speakerPoolId,
            String username,
            String speakerName,
            String eventCode,
            String eventTitle,
            String eventDate,
            String sessionTitle,
            String responseDeadline,
            String invitationMessage,
            boolean alreadyResponded,
            String previousResponse,
            Instant previousResponseDate,
            TokenAction action) {
        return new TokenValidationResult(
                true, null, speakerPoolId, username, speakerName,
                eventCode, eventTitle, eventDate, sessionTitle,
                responseDeadline, invitationMessage,
                alreadyResponded, previousResponse, previousResponseDate, action);
    }

    /**
     * Create a valid result with minimal context (backwards compatible).
     * Used by tests that don't need full context.
     */
    public static TokenValidationResult valid(
            UUID speakerPoolId, String username, String eventCode, TokenAction action) {
        return new TokenValidationResult(
                true, null, speakerPoolId, username, null,
                eventCode, null, null, null, null, null,
                false, null, null, action);
    }

    /**
     * Create an expired token result.
     */
    public static TokenValidationResult expired() {
        return new TokenValidationResult(
                false, "EXPIRED", null, null, null, null, null, null, null, null, null,
                false, null, null, null);
    }

    /**
     * Create an already used token result.
     */
    public static TokenValidationResult alreadyUsed() {
        return new TokenValidationResult(
                false, "ALREADY_USED", null, null, null, null, null, null, null, null, null,
                false, null, null, null);
    }

    /**
     * Create a not found token result.
     */
    public static TokenValidationResult notFound() {
        return new TokenValidationResult(
                false, "NOT_FOUND", null, null, null, null, null, null, null, null, null,
                false, null, null, null);
    }
}
