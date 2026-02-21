package ch.batbern.companyuser.watch.dto;

import java.time.LocalDateTime;

/**
 * Response DTO for pairing code generation.
 * Story W2.1: AC1 — Generate Pairing Code
 * ADR-003: No UUIDs exposed
 */
public record PairingCodeResponse(
        String pairingCode,
        LocalDateTime expiresAt,
        long hoursUntilExpiry
) {}
