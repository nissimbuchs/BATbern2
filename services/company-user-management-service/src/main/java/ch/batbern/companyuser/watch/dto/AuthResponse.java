package ch.batbern.companyuser.watch.dto;

import java.time.LocalDateTime;

/**
 * Response body for POST /api/v1/watch/authenticate.
 * W2.2: Returns JWT and expiry timestamp.
 */
public record AuthResponse(
        String jwt,
        LocalDateTime expiresAt
) {}
