package ch.batbern.events.controller;

import ch.batbern.events.core.api.generated.PublishingEngineApi;
import ch.batbern.events.core.dto.generated.AutoPublishScheduleRequest;
import ch.batbern.events.core.dto.generated.AutoPublishScheduleResponse;
import ch.batbern.events.core.dto.generated.PublishPhaseResponse;
import ch.batbern.events.core.dto.generated.PublishPreviewResponse;
import ch.batbern.events.core.dto.generated.PublishingStatusResponse;
import ch.batbern.events.core.dto.generated.UnpublishPhaseResponse;
import ch.batbern.events.dto.PublishValidationError;
import ch.batbern.events.service.publishing.PublishingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST Controller for Publishing Engine.
 * Story BAT-11 (5.7): Slot Assignment & Progressive Publishing.
 *
 * <p>API-consolidation Phase 7: implements the generated {@link PublishingEngineApi}.
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Slf4j
public class PublishingEngineController implements PublishingEngineApi {

    private final PublishingService publishingService;

    @Override
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<PublishPhaseResponse> publishPhase(String eventCode, String phase) {
        log.info("Publishing phase {} for event {}", phase, eventCode);
        return ResponseEntity.ok(publishingService.publishPhase(eventCode, phase));
    }

    @Override
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<UnpublishPhaseResponse> unpublishPhase(String eventCode, String phase) {
        log.info("Unpublishing phase {} for event {}", phase, eventCode);
        return ResponseEntity.ok(publishingService.unpublishPhase(eventCode, phase));
    }

    @Override
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<PublishingStatusResponse> getPublishingStatus(String eventCode) {
        log.info("Getting publishing status for event {}", eventCode);
        return ResponseEntity.ok(publishingService.getPublishingStatus(eventCode));
    }

    @Override
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<PublishPreviewResponse> getPublishPreview(String eventCode) {
        log.info("Getting preview for event {}", eventCode);
        return ResponseEntity.ok(publishingService.getPreview(eventCode));
    }

    @Override
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<AutoPublishScheduleResponse> configureAutoPublish(
            String eventCode,
            AutoPublishScheduleRequest request) {
        log.info("Configuring auto-publish schedule for event {}", eventCode);
        return ResponseEntity.ok(publishingService.configureAutoPublish(eventCode, request));
    }

    @Override
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<AutoPublishScheduleResponse> getAutoPublishSchedule(String eventCode) {
        log.info("Getting auto-publish schedule for event {}", eventCode);
        return ResponseEntity.ok(publishingService.getAutoPublishSchedule(eventCode));
    }

    /**
     * Exception handler for publishing validation errors.
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
