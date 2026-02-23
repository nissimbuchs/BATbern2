package ch.batbern.partners.dto;

/**
 * Request body for updating topic status (organizer only) — Story 8.2 AC4.
 */
public record TopicStatusUpdateRequest(
        String status,
        String plannedEvent
) {}
