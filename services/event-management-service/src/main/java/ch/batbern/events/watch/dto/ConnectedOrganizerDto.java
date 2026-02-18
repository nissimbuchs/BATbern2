package ch.batbern.events.watch.dto;

/**
 * Organizer presence record broadcast in STATE_UPDATE messages.
 * W4.1 Task 8 (AC4): Presence indicator shows connected organizer count.
 */
public record ConnectedOrganizerDto(
        String username,
        String firstName,
        boolean connected
) {}
