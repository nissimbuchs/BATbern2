package ch.batbern.events.watch.dto;

/**
 * Action payload sent by Watch clients via STOMP to /app/watch/events/{eventCode}/action.
 * W4.1 Task 9.3: Stub handler — W4.2+ implements actual session state mutations.
 */
public record WatchActionMessage(
        String type,
        String sessionSlug,
        Integer minutes
) {}
