package ch.batbern.events.watch.dto;

/**
 * Server-authoritative arrived/total counts broadcast to all watches.
 * W2.4: FR39 — real-time counter sync.
 */
public record ArrivalCount(int arrived, int total) {}
