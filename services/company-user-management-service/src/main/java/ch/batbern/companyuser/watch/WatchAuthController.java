package ch.batbern.companyuser.watch;

import ch.batbern.companyuser.api.generated.WatchAuthenticationApi;
import ch.batbern.companyuser.domain.User;
import ch.batbern.companyuser.domain.WatchPairing;
import ch.batbern.companyuser.dto.generated.WatchAuthRequest;
import ch.batbern.companyuser.dto.generated.WatchAuthResponse;
import ch.batbern.companyuser.dto.generated.WatchPairingRequest;
import ch.batbern.companyuser.dto.generated.WatchPairingResponse;
import ch.batbern.companyuser.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * REST endpoints for Watch pairing and authentication, implementing the generated
 * {@link WatchAuthenticationApi} (users-api).
 * W2.2: AC2 (pair code exchange), AC3 (error handling), AC4 (JWT for persistent pairing).
 *
 * Both endpoints are unauthenticated — the code/token IS the credential.
 * Security is configured in SecurityConfig to permit these paths. Errors answer the
 * {@code ErrorResponse} users-api names (400, 401, 500), through {@link ResponseStatusException}.
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Slf4j
public class WatchAuthController implements WatchAuthenticationApi {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final WatchPairingService watchPairingService;
    private final WatchJwtService watchJwtService;
    private final UserRepository userRepository;

    /**
     * AC2/AC3: Exchange a 6-digit pairing code for a long-lived pairing token.
     *
     * POST /api/v1/watch/pair
     * Request:  { "pairingCode": "123456" }
     * Response: { "pairingToken": "...", "organizerUsername": "...", "organizerFirstName": "..." }
     *
     * M1 fix: validate + clear + save in a single atomic transaction via claimPairingCode().
     * M5 fix: handles user-not-found gracefully (500 with an error body).
     * Returns 400 if code is invalid or expired.
     */
    @Override
    @PreAuthorize("permitAll()")
    public ResponseEntity<WatchPairingResponse> pairWatch(WatchPairingRequest request) {
        // Generate token first — claimPairingCode() then atomically validates + persists
        String pairingToken = generateSecurePairingToken();
        Optional<WatchPairing> claimedOpt =
                watchPairingService.claimPairingCode(request.getPairingCode(), pairingToken);

        if (claimedOpt.isEmpty()) {
            log.warn("Pairing attempt with invalid/expired code");
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Code invalid or expired");
        }

        WatchPairing pairing = claimedOpt.get();

        // M5: Handle user deleted between code generation and pairing
        Optional<User> organizerOpt = userRepository.findByUsername(pairing.getUsername());
        if (organizerOpt.isEmpty()) {
            log.error("User not found for claimed pairing: {}", pairing.getUsername());
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Internal error during pairing");
        }

        User organizer = organizerOpt.get();
        log.info("Watch paired successfully for user: {}", pairing.getUsername());

        return ResponseEntity.ok(WatchPairingResponse.builder()
                .pairingToken(pairingToken)
                .organizerUsername(organizer.getUsername())
                .organizerFirstName(organizer.getFirstName())
                .build());
    }

    /**
     * AC4/NFR16: Exchange a pairing token for a short-lived JWT (1 hour).
     *
     * POST /api/v1/watch/authenticate
     * Request:  { "pairingToken": "..." }
     * Response: { "jwt": "eyJ...", "expiresAt": "2026-02-16T15:30:00Z" }
     *
     * H3+M3 fix: expiresAt is an ISO-8601 UTC timestamp correlated with the generated JWT.
     * Returns 401 if pairing token is invalid or not yet paired.
     */
    @Override
    @PreAuthorize("permitAll()")
    public ResponseEntity<WatchAuthResponse> authenticateWatch(WatchAuthRequest request) {
        Optional<WatchPairing> pairingOpt = watchPairingService.findByPairingToken(request.getPairingToken());

        if (pairingOpt.isEmpty()) {
            log.warn("Authentication attempt with invalid pairing token");
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Pairing token invalid or pairing incomplete");
        }

        WatchPairing pairing = pairingOpt.get();
        // M3+H3: generateTokenWithExpiry() captures jwt + expiresAt atomically from same Instant
        WatchJwtService.WatchJwtResult jwtResult = watchJwtService.generateTokenWithExpiry(pairing.getUsername());

        log.debug("Watch JWT issued for user: {}", pairing.getUsername());

        return ResponseEntity.ok(WatchAuthResponse.builder()
                .jwt(jwtResult.jwt())
                .expiresAt(OffsetDateTime.parse(jwtResult.expiresAt()))
                .build());
    }

    // --- Private helpers ---

    /**
     * Generate a cryptographically secure pairing token.
     * Format: UUID + timestamp + random suffix for high entropy.
     */
    private String generateSecurePairingToken() {
        long randomSuffix = Math.abs(SECURE_RANDOM.nextLong());
        return UUID.randomUUID()
                + "-" + System.currentTimeMillis()
                + "-" + randomSuffix;
    }
}
