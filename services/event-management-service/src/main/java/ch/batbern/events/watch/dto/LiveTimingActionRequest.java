package ch.batbern.events.watch.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Story 15.1: a live agenda timing action applied by an organizer during a LIVE event,
 * the REST replacement for the STOMP {@code /app/watch/events/{eventCode}/action} publish.
 *
 * @param type        the action: END_SESSION, EXTEND_SESSION, or DELAY_TO_PREVIOUS
 * @param sessionSlug slug of the target session (the current session for EXTEND/DELAY)
 * @param minutes     minutes to add (negative reduces) — required for EXTEND/DELAY, ignored for END
 */
public record LiveTimingActionRequest(
        @NotNull LiveTimingActionType type,
        @NotBlank String sessionSlug,
        Integer minutes
) {
    /** The three live-timing actions (mirrors the cascade methods on WatchSessionService). */
    public enum LiveTimingActionType {
        END_SESSION,
        EXTEND_SESSION,
        DELAY_TO_PREVIOUS
    }
}
