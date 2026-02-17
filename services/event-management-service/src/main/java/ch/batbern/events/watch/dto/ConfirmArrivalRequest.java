package ch.batbern.events.watch.dto;

/**
 * REST fallback request body for POST /api/v1/watch/events/{eventCode}/arrivals.
 * W2.4: Offline fallback when WebSocket is disconnected.
 */
public record ConfirmArrivalRequest(String speakerUsername) {}
