package ch.batbern.companyuser.watch.dto;

/**
 * Response body for POST /api/v1/watch/pair.
 * W2.2: Returns pairing token and organizer details after successful code exchange.
 */
public record PairingResponse(
        String pairingToken,
        String organizerUsername,
        String organizerFirstName
) {}
