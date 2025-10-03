package ch.batbern.gateway.gdpr;

import ch.batbern.shared.security.AuditLog;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;

/**
 * GDPR Data Deletion Controller
 *
 * Provides endpoint for users to delete all their personal data.
 * Uses two-step confirmation process for safety.
 *
 * Implements AC9: GDPR Right to Deletion from Story 1.11
 */
@RestController
@RequestMapping("/api/v1/gdpr")
@RequiredArgsConstructor
@Slf4j
public class UserDataDeletionController {

    private final UserDataDeletionService deletionService;

    /**
     * Delete all personal data for the authenticated user
     *
     * Two-step process:
     * 1. First call (no token): Initiates deletion and returns confirmation token
     * 2. Second call (with token): Validates token and performs actual deletion
     *
     * @param jwt The authenticated user's JWT token
     * @param confirmationToken Optional confirmation token from step 1
     * @return Response indicating deletion status
     */
    @DeleteMapping("/delete")
    @AuditLog(action = "USER_DATA_DELETION", severity = AuditLog.AuditSeverity.CRITICAL)
    public ResponseEntity<Map<String, Object>> deleteUserData(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) String confirmationToken) {

        String userId = jwt != null ? jwt.getSubject() : "anonymous";

        log.warn("User data deletion requested for user: {}", userId);

        // Step 1: Initiate deletion - generate confirmation token
        if (confirmationToken == null) {
            String token = deletionService.initiateDeletion(userId);

            return ResponseEntity.ok(Map.of(
                "message", "Deletion initiated. Check your email for confirmation.",
                "confirmationRequired", true,
                "note", "For testing: Use confirmation token in request parameter"
            ));
        }

        // Step 2: Validate confirmation token
        if (!deletionService.validateConfirmationToken(userId, confirmationToken)) {
            return ResponseEntity.badRequest().body(Map.of(
                "error", "Invalid or expired confirmation token",
                "message", "Please request a new deletion confirmation"
            ));
        }

        // Step 3: Perform cascade deletion across all services
        try {
            deletionService.deleteUserProfile(userId);
            deletionService.deleteUserEvents(userId);
            deletionService.deleteSpeakerSubmissions(userId);
            deletionService.deletePartnerAnalytics(userId);
            deletionService.deleteUserPreferences(userId);
            deletionService.deleteCognitoUser(userId);

            log.warn("User data deletion completed for user: {}", userId);

            return ResponseEntity.ok(Map.of(
                "message", "All user data has been permanently deleted",
                "userId", userId,
                "deletionTimestamp", Instant.now()
            ));

        } catch (Exception e) {
            log.error("Error during user data deletion for user: {}", userId, e);

            return ResponseEntity.internalServerError().body(Map.of(
                "error", "Deletion failed",
                "message", "An error occurred during data deletion. Please contact support."
            ));
        }
    }
}
