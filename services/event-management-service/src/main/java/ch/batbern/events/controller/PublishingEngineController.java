package ch.batbern.events.controller;

import ch.batbern.events.dto.AutoPublishScheduleRequest;
import ch.batbern.events.dto.AutoPublishScheduleResponse;
import ch.batbern.events.dto.ChangeLogResponse;
import ch.batbern.events.dto.PublishPhaseResponse;
import ch.batbern.events.dto.PublishPreviewResponse;
import ch.batbern.events.dto.PublishValidationError;
import ch.batbern.events.dto.PublishingStatusResponse;
import ch.batbern.events.dto.RollbackResponse;
import ch.batbern.events.dto.UnpublishPhaseResponse;
import ch.batbern.events.dto.VersionHistoryResponse;
import ch.batbern.events.service.publishing.PublishingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST Controller for Publishing Engine
 * Story BAT-11 (5.7): Slot Assignment & Progressive Publishing
 * Task 3b: GREEN Phase - Publishing Engine Implementation
 *
 * Endpoints:
 * - POST /api/v1/events/{eventCode}/publish/{phase} - Publish a phase
 * - POST /api/v1/events/{eventCode}/unpublish/{phase} - Unpublish a phase
 * - GET /api/v1/events/{eventCode}/publish/status - Get publishing validation status
 * - GET /api/v1/events/{eventCode}/publish/preview - Get preview
 * - GET /api/v1/events/{eventCode}/publish/versions - Get version history
 * - POST /api/v1/events/{eventCode}/publish/rollback/{versionNumber} - Rollback
 * - GET /api/v1/events/{eventCode}/publish/changelog - Get change log
 * - POST /api/v1/events/{eventCode}/publish/schedule - Configure auto-publish
 * - GET /api/v1/events/{eventCode}/publish/schedule - Get auto-publish schedule
 */
@RestController
@RequestMapping("/api/v1/events")
@RequiredArgsConstructor
@Slf4j
public class PublishingEngineController {

    private final PublishingService publishingService;

    /**
     * Publish a specific phase (topic, speakers, or agenda)
     * AC14-16: Progressive publishing phases
     */
    @PostMapping("/{eventCode}/publish/{phase}")
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<PublishPhaseResponse> publishPhase(
            @PathVariable String eventCode,
            @PathVariable String phase,
            Authentication authentication) {

        log.info("Publishing phase {} for event {}", phase, eventCode);

        String publishedBy = authentication != null ? authentication.getName() : "system";

        PublishPhaseResponse response = publishingService.publishPhase(eventCode, phase, publishedBy);

        return ResponseEntity.ok(response);
    }

    /**
     * Unpublish a specific phase
     * AC18: Manual unpublish buttons per phase
     */
    @PostMapping("/{eventCode}/unpublish/{phase}")
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<UnpublishPhaseResponse> unpublishPhase(
            @PathVariable String eventCode,
            @PathVariable String phase) {

        log.info("Unpublishing phase {} for event {}", phase, eventCode);

        UnpublishPhaseResponse response = publishingService.unpublishPhase(eventCode, phase);

        return ResponseEntity.ok(response);
    }

    /**
     * Get publishing status including validation for all phases
     * Used by frontend to display current publishing state and validation errors
     */
    @GetMapping("/{eventCode}/publish/status")
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<PublishingStatusResponse> getPublishingStatus(@PathVariable String eventCode) {

        log.info("Getting publishing status for event {}", eventCode);

        PublishingStatusResponse response = publishingService.getPublishingStatus(eventCode);

        return ResponseEntity.ok(response);
    }

    /**
     * Get publishing preview
     * AC20, AC29: Preview mode to see public appearance before publishing
     */
    @GetMapping("/{eventCode}/publish/preview")
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<PublishPreviewResponse> getPreview(@PathVariable String eventCode) {

        log.info("Getting preview for event {}", eventCode);

        PublishPreviewResponse response = publishingService.getPreview(eventCode);

        return ResponseEntity.ok(response);
    }

    /**
     * Get version history
     * AC26: Track all publishing versions with timestamp
     */
    @GetMapping("/{eventCode}/publish/versions")
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<VersionHistoryResponse> getVersionHistory(@PathVariable String eventCode) {

        log.info("Getting version history for event {}", eventCode);

        VersionHistoryResponse response = publishingService.getVersionHistory(eventCode);

        return ResponseEntity.ok(response);
    }

    /**
     * Rollback to a previous version
     * AC27: Rollback to previous version capability
     */
    @PostMapping("/{eventCode}/publish/rollback/{versionNumber}")
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<RollbackResponse> rollbackVersion(
            @PathVariable String eventCode,
            @PathVariable Integer versionNumber) {

        log.info("Rolling back event {} to version {}", eventCode, versionNumber);

        RollbackResponse response = publishingService.rollbackToVersion(eventCode, versionNumber);

        return ResponseEntity.ok(response);
    }

    /**
     * Get change log
     * AC28: Change log for all post-publish updates
     */
    @GetMapping("/{eventCode}/publish/changelog")
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<ChangeLogResponse> getChangeLog(@PathVariable String eventCode) {

        log.info("Getting change log for event {}", eventCode);

        ChangeLogResponse response = publishingService.getChangeLog(eventCode);

        return ResponseEntity.ok(response);
    }

    /**
     * Configure auto-publish schedule
     * AC19: Auto-publish scheduling: Phase 2 at 1 month, Phase 3 at 2 weeks before event
     */
    @PostMapping("/{eventCode}/publish/schedule")
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<AutoPublishScheduleResponse> configureAutoPublish(
            @PathVariable String eventCode,
            @Valid @RequestBody AutoPublishScheduleRequest request) {

        log.info("Configuring auto-publish schedule for event {}", eventCode);

        AutoPublishScheduleResponse response = publishingService.configureAutoPublish(eventCode, request);

        return ResponseEntity.ok(response);
    }

    /**
     * Get auto-publish schedule
     */
    @GetMapping("/{eventCode}/publish/schedule")
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<AutoPublishScheduleResponse> getAutoPublishSchedule(@PathVariable String eventCode) {

        log.info("Getting auto-publish schedule for event {}", eventCode);

        AutoPublishScheduleResponse response = publishingService.getAutoPublishSchedule(eventCode);

        return ResponseEntity.ok(response);
    }

    /**
     * Finalize agenda
     * Story BAT-16 (AC7): Agenda Finalization Controls
     *
     * Prerequisites:
     * - Event must be in AGENDA_PUBLISHED state
     * - Event date must be at least 14 days in the future
     */
    @PostMapping("/{eventCode}/finalize")
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<ch.batbern.events.dto.EventResponse> finalizeAgenda(
            @PathVariable String eventCode,
            Authentication authentication) {

        log.info("Finalizing agenda for event {}", eventCode);

        String organizerUsername = authentication != null ? authentication.getName() : "system";

        ch.batbern.events.domain.Event event = publishingService.finalizeAgenda(eventCode, organizerUsername);

        // Convert to response DTO (simplified)
        ch.batbern.events.dto.EventResponse response = ch.batbern.events.dto.EventResponse.builder()
                .eventCode(event.getEventCode())
                .workflowState(event.getWorkflowState().name())
                .build();

        return ResponseEntity.ok(response);
    }

    /**
     * Unfinalize agenda
     * Story BAT-16 (AC7): Agenda Finalization Controls
     *
     * Requires explicit reason for audit trail
     */
    @PostMapping("/{eventCode}/unfinalize")
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<ch.batbern.events.dto.EventResponse> unfinalizeAgenda(
            @PathVariable String eventCode,
            @RequestBody UnfinalizeRequest request,
            Authentication authentication) {

        log.info("Unfinalizing agenda for event {}", eventCode);

        String organizerUsername = authentication != null ? authentication.getName() : "system";

        ch.batbern.events.domain.Event event = publishingService.unfinalizeAgenda(
                eventCode, request.reason(), organizerUsername);

        // Convert to response DTO (simplified)
        ch.batbern.events.dto.EventResponse response = ch.batbern.events.dto.EventResponse.builder()
                .eventCode(event.getEventCode())
                .workflowState(event.getWorkflowState().name())
                .build();

        return ResponseEntity.ok(response);
    }

    /**
     * Request DTO for unfinalizing agenda
     */
    public record UnfinalizeRequest(String reason) {}

    /**
     * Exception handler for publishing validation errors
     */
    @ExceptionHandler(PublishingService.PublishValidationException.class)
    public ResponseEntity<PublishValidationError> handleValidationException(
            PublishingService.PublishValidationException ex) {
        PublishValidationError error = PublishValidationError.builder()
                .error("VALIDATION_ERROR")
                .message(ex.getMessage())
                .validationErrors(ex.getValidationErrors())
                .build();

        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(error);
    }
}
