package ch.batbern.companyuser.watch;

import ch.batbern.companyuser.api.generated.WatchPairingApi;
import ch.batbern.companyuser.dto.generated.PairedWatch;
import ch.batbern.companyuser.dto.generated.PairingCodeResponse;
import ch.batbern.companyuser.dto.generated.PairingStatusResponse;
import ch.batbern.companyuser.dto.generated.PendingPairingCode;
import ch.batbern.companyuser.security.SecurityContextHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * REST controller for Watch pairing code management, implementing the generated
 * {@link WatchPairingApi} (users-api). Story W2.1: Pairing Code Backend & Web Frontend
 *
 * AC1: POST generate pairing code
 * AC2: 409 Conflict when max watches reached
 * AC4: DELETE unpair watch
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Slf4j
public class WatchPairingController implements WatchPairingApi {

    private final WatchPairingService watchPairingService;
    private final SecurityContextHelper securityContextHelper;

    /**
     * AC1: Generate pairing code (201 Created).
     * AC2: 409 Conflict if max watches already paired.
     * Authorization: ORGANIZER only, own account only.
     */
    @Override
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<PairingCodeResponse> generateWatchPairingCode(String username) {
        requireOwnAccount(username);
        var code = watchPairingService.generatePairingCode(username);
        return ResponseEntity.status(HttpStatus.CREATED).body(PairingCodeResponse.builder()
                .pairingCode(code.pairingCode())
                .expiresAt(utc(code.expiresAt()))
                .hoursUntilExpiry(Math.toIntExact(code.hoursUntilExpiry()))
                .build());
    }

    /**
     * Check pairing status — returns paired devices + any pending code.
     * Authorization: ORGANIZER only, own account only.
     */
    @Override
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<PairingStatusResponse> getWatchPairingStatus(String username) {
        requireOwnAccount(username);
        var status = watchPairingService.getPairingStatus(username);
        var pending = status.pendingCode();
        return ResponseEntity.ok(PairingStatusResponse.builder()
                .pairedWatches(status.pairedWatches().stream()
                        .map(w -> PairedWatch.builder()
                                .id(w.id())
                                .deviceName(w.deviceName())
                                .pairedAt(utc(w.pairedAt()))
                                .build())
                        .toList())
                .pendingCode(pending == null ? null : PendingPairingCode.builder()
                        .code(pending.code())
                        .expiresAt(utc(pending.expiresAt()))
                        .build())
                .build());
    }

    /**
     * AC4: Unpair a watch by its UUID (204 No Content).
     * Uses UUID instead of deviceName — deviceName is nullable (not set during pairing flow).
     * Authorization: ORGANIZER only, own account only.
     */
    @Override
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<Void> unpairWatch(String username, UUID watchId) {
        requireOwnAccount(username);
        watchPairingService.unpairWatch(username, watchId);
        return ResponseEntity.noContent().build();
    }

    private void requireOwnAccount(String username) {
        String currentUsername = securityContextHelper.getCurrentUsername();
        if (!currentUsername.equals(username)) {
            throw new AccessDeniedException("You can only manage your own watch pairings");
        }
    }

    private static OffsetDateTime utc(Instant instant) {
        return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
    }
}
