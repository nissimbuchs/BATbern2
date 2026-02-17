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
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.SecureRandom;
import java.time.LocalDateTime;
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
     * Returns 400 if code is invalid or expired.
     */
    @PostMapping("/pair")
    public ResponseEntity<PairingResponse> pair(@Valid @RequestBody PairingRequest request) {
        Optional<WatchPairing> pairingOpt = watchPairingService.validatePairingCode(request.pairingCode());

        if (pairingOpt.isEmpty()) {
            log.warn("Pairing attempt with invalid/expired code");
            return ResponseEntity.badRequest().build();
        }

        WatchPairing pairing = pairingOpt.get();

        // Generate cryptographically secure pairing token
        String pairingToken = generateSecurePairingToken();

        // Persist pairing token and mark as paired
        pairing.setPairingToken(pairingToken);
        pairing.setPairedAt(LocalDateTime.now());
        pairing.clearPairingCode();  // Single-use — clear after successful exchange

        // Note: WatchPairingService.save is not exposed; use repository directly via service.
        // We use a transactional update through WatchPairingRepository via the service layer.
        watchPairingService.saveCompletedPairing(pairing);

        // Fetch organizer details
        User organizer = userRepository.findByUsername(pairing.getUsername())
                .orElseThrow(() -> new IllegalStateException(
                        "User not found for pairing: " + pairing.getUsername()));

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
     * Response: { "jwt": "eyJ...", "expiresAt": "2026-02-16T15:30:00" }
     *
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
        String jwt = watchJwtService.generateToken(pairing.getUsername());
        LocalDateTime expiresAt = watchJwtService.getExpiresAt();

        log.debug("Watch JWT issued for user: {}", pairing.getUsername());

        return ResponseEntity.ok(new AuthResponse(jwt, expiresAt));
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
