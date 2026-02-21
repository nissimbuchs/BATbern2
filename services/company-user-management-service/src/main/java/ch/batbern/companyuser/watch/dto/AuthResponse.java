package ch.batbern.companyuser.watch.dto;

/**
 * Response body for POST /api/v1/watch/authenticate.
 * W2.2: Returns JWT and expiry timestamp.
 *
 * H3 fix: expiresAt is ISO-8601 UTC string (e.g. "2026-02-16T15:30:00Z").
 * LocalDateTime was serialized without timezone, causing Swift ISO8601DateFormatter
 * to silently fall back to a wrong expiry, breaking NFR16 auto-refresh timing.
 */
public record AuthResponse(
        String jwt,
        String expiresAt
) {}
