package ch.batbern.companyuser.watch;

import ch.batbern.companyuser.security.SecurityContextHelper;
import ch.batbern.companyuser.watch.dto.PairingCodeResponse;
import ch.batbern.companyuser.watch.dto.PairingStatusResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.access.AccessDeniedException;

import java.util.UUID;

/**
 * REST controller for Watch pairing code management.
 * Story W2.1: Pairing Code Backend & Web Frontend
 *
 * AC1: POST generate pairing code
 * AC2: 409 Conflict when max watches reached
 * AC4: DELETE unpair watch
 */
@RestController
@RequestMapping("/api/v1/users/{username}/watch-pairing")
@RequiredArgsConstructor
@Slf4j
public class WatchPairingController {

    private final WatchPairingService watchPairingService;
    private final SecurityContextHelper securityContextHelper;

    /**
     * AC1: Generate pairing code (201 Created).
     * AC2: 409 Conflict if max watches already paired.
     * Authorization: ORGANIZER only, own account only.
     */
    @PostMapping
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<PairingCodeResponse> generatePairingCode(
            @PathVariable String username) {

        requireOwnAccount(username);
        PairingCodeResponse response = watchPairingService.generatePairingCode(username);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Check pairing status — returns paired devices + any pending code.
     * Authorization: ORGANIZER only, own account only.
     */
    @GetMapping
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<PairingStatusResponse> getPairingStatus(
            @PathVariable String username) {

        requireOwnAccount(username);
        PairingStatusResponse response = watchPairingService.getPairingStatus(username);
        return ResponseEntity.ok(response);
    }

    /**
     * AC4: Unpair a watch by its UUID (204 No Content).
     * Uses UUID instead of deviceName — deviceName is nullable (not set during pairing flow).
     * Authorization: ORGANIZER only, own account only.
     */
    @DeleteMapping("/{watchId}")
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<Void> unpairWatch(
            @PathVariable String username,
            @PathVariable UUID watchId) {

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
}
