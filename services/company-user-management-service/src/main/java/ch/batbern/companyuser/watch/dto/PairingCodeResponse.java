package ch.batbern.companyuser.watch.dto;

import java.time.Instant;

/**
 * Response DTO for pairing code generation.
 * Story W2.1: AC1 — Generate Pairing Code
 * ADR-003: No UUIDs exposed
 * expiresAt is an Instant so it serialises with a zone ({@code Z}), as users-api's
 * {@code format: date-time} requires.
 */
public record PairingCodeResponse(
        String pairingCode,
        Instant expiresAt,
        long hoursUntilExpiry
) {}
