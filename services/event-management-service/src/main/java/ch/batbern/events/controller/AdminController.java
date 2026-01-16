package ch.batbern.events.controller;

import ch.batbern.events.service.SpeakerNameBackfillService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Admin Controller for maintenance operations
 * TEMPORARY: Remove or secure with admin authentication before production
 */
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@Slf4j
public class AdminController {

    private final SpeakerNameBackfillService backfillService;

    /**
     * Backfill speaker names for all session_users with NULL speaker_first_name
     * This enables full-text search on speaker names for archive browsing.
     *
     * TEMPORARY ENDPOINT: Remove or secure before production deployment
     *
     * @return Number of records updated
     */
    @PostMapping("/backfill-speaker-names")
    public ResponseEntity<Map<String, Object>> backfillSpeakerNames() {
        log.info("Admin endpoint called: backfill-speaker-names");

        try {
            int updatedCount = backfillService.backfillSpeakerNames();

            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Speaker names backfilled successfully",
                "updatedCount", updatedCount
            ));

        } catch (Exception e) {
            log.error("Failed to backfill speaker names", e);
            return ResponseEntity.internalServerError().body(Map.of(
                "success", false,
                "message", "Failed to backfill speaker names: " + e.getMessage()
            ));
        }
    }
}
