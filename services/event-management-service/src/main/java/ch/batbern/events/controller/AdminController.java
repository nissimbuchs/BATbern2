package ch.batbern.events.controller;

import ch.batbern.events.core.api.generated.AdminApi;
import ch.batbern.events.core.dto.generated.BackfillSpeakerNamesResponse;
import ch.batbern.events.service.SpeakerNameBackfillService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin Controller for maintenance operations.
 * TEMPORARY: Remove or secure with admin authentication before production.
 *
 * <p>API-consolidation Phase 7: implements the generated {@link AdminApi}.
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Slf4j
public class AdminController implements AdminApi {

    private final SpeakerNameBackfillService backfillService;

    @Override
    public ResponseEntity<BackfillSpeakerNamesResponse> backfillSpeakerNames() {
        log.info("Admin endpoint called: backfill-speaker-names");

        try {
            int updatedCount = backfillService.backfillSpeakerNames();
            return ResponseEntity.ok(BackfillSpeakerNamesResponse.builder()
                    .success(true)
                    .message("Speaker names backfilled successfully")
                    .updatedCount(updatedCount)
                    .build());
        } catch (Exception e) {
            log.error("Failed to backfill speaker names", e);
            return ResponseEntity.internalServerError().body(BackfillSpeakerNamesResponse.builder()
                    .success(false)
                    .message("Failed to backfill speaker names: " + e.getMessage())
                    .build());
        }
    }
}
