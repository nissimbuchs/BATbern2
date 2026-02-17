package ch.batbern.companyuser.watch.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Response DTO for pairing status queries.
 * Story W2.1: GET /api/v1/users/{username}/watch-pairing
 * ADR-003: Uses deviceName (not UUID) as identifier
 */
public record PairingStatusResponse(
        List<PairedWatch> pairedWatches,
        PendingPairingCode pendingCode
) {

    public record PairedWatch(
            String deviceName,
            LocalDateTime pairedAt
    ) {}

    public record PendingPairingCode(
            String code,
            LocalDateTime expiresAt
    ) {}
}
