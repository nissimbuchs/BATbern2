package ch.batbern.companyuser.watch;

import ch.batbern.companyuser.domain.User;
import ch.batbern.companyuser.domain.WatchPairing;
import ch.batbern.companyuser.repository.UserRepository;
import ch.batbern.companyuser.watch.dto.AuthRequest;
import ch.batbern.companyuser.watch.dto.AuthResponse;
import ch.batbern.companyuser.watch.dto.PairingRequest;
import ch.batbern.companyuser.watch.dto.PairingResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.SecureRandom;
import java.util.Map;
import java.util.Optional;

/**
 * REST endpoints for Watch pairing and authentication.
 * W2.2: AC2 (pair code exchange), AC3 (error handling), AC4 (JWT for persistent pairing).
 *
 * Both endpoints are unauthenticated — the code/token IS the credential.
 * Security is configured in SecurityConfig to permit these paths.
 */
@RestController
@RequestMapping("/api/v1/watch")
@RequiredArgsConstructor
@Slf4j
public class WatchAuthController {

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
     * M2 fix: returns error body with message on 400, not empty body.
     * M5 fix: handles user-not-found gracefully instead of throwing 500.
     * Returns 400 if code is invalid or expired.
     */
    @PostMapping("/pair")
    public ResponseEntity<?> pair(@Valid @RequestBody PairingRequest request) {
        // Generate token first — claimPairingCode() then atomically validates + persists
        String pairingToken = generateSecurePairingToken();
        Optional<WatchPairing> claimedOpt = watchPairingService.claimPairingCode(request.pairingCode(), pairingToken);

        if (claimedOpt.isEmpty()) {
            log.warn("Pairing attempt with invalid/expired code");
            // M2: Return error body per story task 3.3 spec
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "Code invalid or expired"));
        }

        WatchPairing pairing = claimedOpt.get();

        // M5: Handle user deleted between code generation and pairing
        Optional<User> organizerOpt = userRepository.findByUsername(pairing.getUsername());
        if (organizerOpt.isEmpty()) {
            log.error("User not found for claimed pairing: {}", pairing.getUsername());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Internal error during pairing"));
        }

        User organizer = organizerOpt.get();
        log.info("Watch paired successfully for user: {}", pairing.getUsername());

        return ResponseEntity.ok(new PairingResponse(
                pairingToken,
                organizer.getUsername(),
                organizer.getFirstName()
        ));
    }

    /**
     * AC4/NFR16: Exchange a pairing token for a short-lived JWT (1 hour).
     *
     * POST /api/v1/watch/authenticate
     * Request:  { "pairingToken": "..." }
     * Response: { "jwt": "eyJ...", "expiresAt": "2026-02-16T15:30:00Z" }
     *
     * H3+M3 fix: expiresAt is now an ISO-8601 UTC string correlated with the generated JWT.
     * Returns 401 if pairing token is invalid or not yet paired.
     */
    @PostMapping("/authenticate")
    public ResponseEntity<AuthResponse> authenticate(@Valid @RequestBody AuthRequest request) {
        Optional<WatchPairing> pairingOpt = watchPairingService.findByPairingToken(request.pairingToken());

        if (pairingOpt.isEmpty()) {
            log.warn("Authentication attempt with invalid pairing token");
            return ResponseEntity.status(401).build();
        }

        WatchPairing pairing = pairingOpt.get();
        // M3+H3: generateTokenWithExpiry() captures jwt + expiresAt atomically from same Instant
        WatchJwtService.WatchJwtResult jwtResult = watchJwtService.generateTokenWithExpiry(pairing.getUsername());

        log.debug("Watch JWT issued for user: {}", pairing.getUsername());

        return ResponseEntity.ok(new AuthResponse(jwtResult.jwt(), jwtResult.expiresAt()));
    }

    // --- Private helpers ---

    /**
     * Generate a cryptographically secure pairing token.
     * Format: UUID + timestamp + random suffix for high entropy.
     */
    private String generateSecurePairingToken() {
        long randomSuffix = Math.abs(SECURE_RANDOM.nextLong());
        return java.util.UUID.randomUUID()
                + "-" + System.currentTimeMillis()
                + "-" + randomSuffix;
    }
}
