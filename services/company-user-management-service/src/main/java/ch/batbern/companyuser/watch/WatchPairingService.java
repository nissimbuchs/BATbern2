package ch.batbern.companyuser.watch;

import ch.batbern.companyuser.domain.WatchPairing;
import ch.batbern.companyuser.exception.MaxWatchesExceededException;
import ch.batbern.companyuser.exception.WatchPairingNotFoundException;
import ch.batbern.companyuser.watch.dto.PairingCodeResponse;
import ch.batbern.companyuser.watch.dto.PairingStatusResponse;
import ch.batbern.companyuser.watch.repository.WatchPairingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Service for Watch pairing code management.
 * Story W2.1: Pairing Code Backend & Web Frontend
 *
 * AC1: Generate 6-digit pairing code with 24h expiry
 * AC2: Enforce max 2 watches per organizer
 * AC3: Handle code expiry
 * AC4: Unpair watch (hard delete)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WatchPairingService {

    private static final int MAX_WATCHES = 2;
    private static final int CODE_TTL_HOURS = 24;
    private static final int MAX_CODE_RETRIES = 10;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final WatchPairingRepository watchPairingRepository;

    /**
     * AC1: Generate a 6-digit pairing code for the organizer.
     * AC2: Rejects if already at max 2 paired watches.
     */
    @Transactional
    public PairingCodeResponse generatePairingCode(String username) {
        // H1: Pessimistic lock prevents two concurrent requests both passing the max-2 check.
        long pairedCount = watchPairingRepository.countPairedWatchesForUpdate(username);
        if (pairedCount >= MAX_WATCHES) {
            throw new MaxWatchesExceededException();
        }

        // H3: Delete any pre-existing pending code rows so we don't accumulate orphans.
        watchPairingRepository.deleteAllPendingCodesByUsername(username);

        String code = generateUniqueCode();

        WatchPairing pairing = new WatchPairing();
        pairing.setUsername(username);
        pairing.setPairingCode(code);
        LocalDateTime expiresAt = LocalDateTime.now().plusHours(CODE_TTL_HOURS);
        pairing.setPairingCodeExpiresAt(expiresAt);

        watchPairingRepository.save(pairing);
        log.info("Generated pairing code for user: {}", username);

        return new PairingCodeResponse(code, expiresAt, CODE_TTL_HOURS);
    }

    /**
     * Returns current pairing status: list of paired devices + any active pending code.
     */
    @Transactional(readOnly = true)
    public PairingStatusResponse getPairingStatus(String username) {
        List<WatchPairing> all = watchPairingRepository.findByUsername(username);

        List<PairingStatusResponse.PairedWatch> pairedWatches = all.stream()
                .filter(WatchPairing::isPaired)
                .map(p -> new PairingStatusResponse.PairedWatch(p.getDeviceName(), p.getPairedAt()))
                .toList();

        // Only return pending code if it exists and is not expired
        PairingStatusResponse.PendingPairingCode pendingCode = all.stream()
                .filter(p -> p.getPairingCode() != null && !p.isCodeExpired())
                .findFirst()
                .map(p -> new PairingStatusResponse.PendingPairingCode(p.getPairingCode(), p.getPairingCodeExpiresAt()))
                .orElse(null);

        return new PairingStatusResponse(pairedWatches, pendingCode);
    }

    /**
     * AC4: Hard-delete the pairing record (GDPR compliant).
     */
    @Transactional
    public void unpairWatch(String username, String deviceName) {
        WatchPairing pairing = watchPairingRepository
                .findByUsernameAndDeviceName(username, deviceName)
                .orElseThrow(() -> new WatchPairingNotFoundException(deviceName));

        watchPairingRepository.delete(pairing);
        log.info("Unpaired watch '{}' for user: {}", deviceName, username);
    }

    // --- Private helpers ---

    /**
     * H2: SecureRandom replaces ThreadLocalRandom — pairing codes are security-sensitive.
     * L1: MAX_CODE_RETRIES cap prevents theoretical infinite loop.
     */
    private String generateUniqueCode() {
        for (int attempt = 0; attempt < MAX_CODE_RETRIES; attempt++) {
            int raw = SECURE_RANDOM.nextInt(900_000) + 100_000; // 100000–999999
            String code = String.valueOf(raw);
            if (watchPairingRepository.findByPairingCode(code).isEmpty()) {
                return code;
            }
        }
        throw new IllegalStateException(
                "Unable to generate a unique pairing code after " + MAX_CODE_RETRIES + " attempts");
    }
}
