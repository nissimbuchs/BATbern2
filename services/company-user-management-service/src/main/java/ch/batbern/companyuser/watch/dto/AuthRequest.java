package ch.batbern.companyuser.watch.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body for POST /api/v1/watch/authenticate.
 * W2.2: Exchange pairing token for short-lived JWT.
 */
public record AuthRequest(
        @NotBlank
        String pairingToken
) {}
