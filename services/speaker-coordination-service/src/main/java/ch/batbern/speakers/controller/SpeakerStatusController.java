package ch.batbern.speakers.controller;

import ch.batbern.speakers.dto.SpeakerStatusResponse;
import ch.batbern.speakers.dto.StatusHistoryItem;
import ch.batbern.speakers.dto.StatusSummaryResponse;
import ch.batbern.speakers.dto.UpdateStatusRequest;
import ch.batbern.speakers.service.SpeakerStatusService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * REST Controller for Speaker Status Management
 * Story 5.4: Speaker Status Management (AC1-18)
 *
 * Endpoints:
 * - PUT  /api/v1/events/{code}/speakers/{speakerId}/status - Update speaker status
 * - GET  /api/v1/events/{code}/speakers/{speakerId}/status/history - Get status history
 * - GET  /api/v1/events/{code}/speakers/status-summary - Get status dashboard summary
 *
 * Security: All endpoints require ORGANIZER role (AC16)
 */
@RestController
@RequestMapping("/api/v1/events/{eventCode}/speakers")
@RequiredArgsConstructor
@Slf4j
public class SpeakerStatusController {

    private final SpeakerStatusService speakerStatusService;

    /**
     * Update speaker status
     * Story 5.4 AC1-2: Manual status updates with workflow validation
     *
     * @param eventCode Event code
     * @param speakerId Speaker pool ID
     * @param request Update status request with new status and optional reason
     * @return Updated speaker status response
     */
    @PutMapping("/{speakerId}/status")
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<SpeakerStatusResponse> updateStatus(
            @PathVariable String eventCode,
            @PathVariable UUID speakerId,
            @Valid @RequestBody UpdateStatusRequest request) {

        log.info("PUT /api/v1/events/{}/speakers/{}/status - newStatus: {}",
                eventCode, speakerId, request.getNewStatus());

        // Extract organizer username from security context
        String organizerUsername = getCurrentUsername();

        SpeakerStatusResponse response = speakerStatusService.updateStatus(
                eventCode,
                speakerId,
                organizerUsername,
                request
        );

        return ResponseEntity.ok(response);
    }

    /**
     * Get speaker status history
     * Story 5.4 AC15: Query status history timeline
     *
     * @param eventCode Event code
     * @param speakerId Speaker pool ID
     * @return List of status changes ordered by time descending
     */
    @GetMapping("/{speakerId}/status/history")
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<List<StatusHistoryItem>> getStatusHistory(
            @PathVariable String eventCode,
            @PathVariable UUID speakerId) {

        log.info("GET /api/v1/events/{}/speakers/{}/status/history", eventCode, speakerId);

        List<StatusHistoryItem> history = speakerStatusService.getStatusHistory(eventCode, speakerId);

        return ResponseEntity.ok(history);
    }

    /**
     * Get status dashboard summary
     * Story 5.4 AC5-6: Visual dashboard with status counts and acceptance rate
     * Story 5.4 AC13: Overflow detection
     *
     * @param eventCode Event code
     * @return Status summary with counts, acceptance rate, and overflow detection
     */
    @GetMapping("/status-summary")
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<StatusSummaryResponse> getStatusSummary(
            @PathVariable String eventCode) {

        log.info("GET /api/v1/events/{}/speakers/status-summary", eventCode);

        StatusSummaryResponse summary = speakerStatusService.getStatusSummary(eventCode);

        return ResponseEntity.ok(summary);
    }

    /**
     * Extract current username from Spring Security context
     */
    private String getCurrentUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getName() != null) {
            return authentication.getName();
        }
        return "system";  // Fallback for tests
    }
}
