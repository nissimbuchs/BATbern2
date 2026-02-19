package ch.batbern.events.watch.dto;

/**
 * DTO representing a single speaker arrival status entry.
 * W2.4: FR38 — arrival status for GET /api/v1/watch/events/{eventCode}/arrivals.
 */
public record ArrivalStatusDto(
        String speakerUsername,
        String confirmedBy,
        String arrivedAt
) {}
