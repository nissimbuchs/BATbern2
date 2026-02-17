package ch.batbern.companyuser.watch.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Request body for POST /api/v1/watch/pair.
 * W2.2: Exchange 6-digit pairing code for pairing token.
 */
public record PairingRequest(
        @NotBlank
        @Pattern(regexp = "\\d{6}", message = "Pairing code must be exactly 6 digits")
        String pairingCode
) {}
