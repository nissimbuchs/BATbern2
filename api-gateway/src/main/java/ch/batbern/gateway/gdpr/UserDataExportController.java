package ch.batbern.gateway.gdpr;

import ch.batbern.shared.security.AuditLog;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * GDPR Data Export Controller
 *
 * Provides endpoint for users to export their personal data in JSON format.
 *
 * Implements AC8: GDPR Data Export from Story 1.11
 */
@RestController
@RequestMapping("/api/v1/gdpr")
@RequiredArgsConstructor
@Slf4j
public class UserDataExportController {

    private final UserDataExportService exportService;

    /**
     * Export all personal data for the authenticated user
     *
     * Returns JSON with all user data from all services:
     * - Profile information
     * - Event registrations
     * - Speaker submissions
     * - Partner analytics
     * - User preferences
     *
     * @param jwt The authenticated user's JWT token
     * @return JSON containing all personal data
     */
    @GetMapping("/export")
    @AuditLog(action = "USER_DATA_EXPORT", severity = AuditLog.AuditSeverity.WARNING)
    public ResponseEntity<Map<String, Object>> exportUserData(
            @AuthenticationPrincipal Jwt jwt) {

        String userId = jwt != null ? jwt.getSubject() : "anonymous";
        String email = jwt != null ? jwt.getClaim("email") : null;

        log.info("User data export requested for user: {}", userId);

        // Collect data from all services
        Map<String, Object> userData = new HashMap<>();
        userData.put("userId", userId);
        userData.put("email", email);
        userData.put("exportDate", Instant.now());

        // Aggregate data from domain services
        userData.put("profile", exportService.exportUserProfile(userId));
        userData.put("events", exportService.exportUserEvents(userId));
        userData.put("submissions", exportService.exportSpeakerSubmissions(userId));
        userData.put("analytics", exportService.exportPartnerAnalytics(userId));
        userData.put("preferences", exportService.exportUserPreferences(userId));

        log.info("User data export completed for user: {}", userId);

        return ResponseEntity.ok(userData);
    }
}
