package ch.batbern.events.watch.dto;

/**
 * WebSocket broadcast payload sent to /topic/events/{eventCode}/arrivals.
 * Carries individual arrival plus server-authoritative counts for real-time sync.
 * W2.4: FR38, FR39 — broadcast to all organizer watches within 3 seconds.
 */
public record SpeakerArrivalBroadcast(
        String type,
        String eventCode,
        String speakerUsername,
        String speakerFirstName,
        String speakerLastName,
        String confirmedBy,
        String arrivedAt,
        ArrivalCount arrivalCount
) {}
