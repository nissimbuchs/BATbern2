package ch.batbern.companyuser.watch.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Response DTO for pairing status queries.
 * Story W2.1: GET /api/v1/users/{username}/watch-pairing
 * Bugfix: Added id (UUID) to PairedWatch so unpair can use a stable, non-null identifier.
 * deviceName is nullable (not set during pairing flow) so cannot be used as identifier.
 * Times are Instants so they serialise with a zone ({@code Z}), as users-api's
 * {@code format: date-time} requires.
 */
public record PairingStatusResponse(
        List<PairedWatch> pairedWatches,
        PendingPairingCode pendingCode
) {

    public record PairedWatch(
            UUID id,
            String deviceName,
            Instant pairedAt
    ) {}

    public record PendingPairingCode(
            String code,
            Instant expiresAt
    ) {}
}
