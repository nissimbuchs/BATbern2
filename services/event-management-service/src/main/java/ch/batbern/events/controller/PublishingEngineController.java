package ch.batbern.events.controller;

import ch.batbern.events.core.api.generated.PublishingEngineApi;
import ch.batbern.events.core.dto.generated.PatchPublishingRequest;
import ch.batbern.events.core.dto.generated.PublishPhaseResponse;
import ch.batbern.events.core.dto.generated.PublishingStatusResponse;
import ch.batbern.events.core.dto.generated.UnpublishPhaseResponse;
import ch.batbern.events.dto.PublishValidationError;
import ch.batbern.events.service.publishing.PublishingPhase;
import ch.batbern.events.service.publishing.PublishingService;
import ch.batbern.shared.dto.ErrorResponse;
import ch.batbern.shared.util.CorrelationIdGenerator;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

/**
 * REST Controller for Publishing Engine.
 * Story BAT-11 (5.7): Slot Assignment & Progressive Publishing.
 *
 * <p>API-consolidation Phase 7: implements the generated {@link PublishingEngineApi}. Story 4.9b:
 * the phase is the addressed instance ({@code POST /events/{eventCode}/phases/{phase}/publish}),
 * status and schedule are one resource ({@code GET/PATCH /events/{eventCode}/publishing}).
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class PublishingEngineController implements PublishingEngineApi {

    private final PublishingService publishingService;

    @Override
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<PublishPhaseResponse> publishPhase(String eventCode, String phase) {
        return ResponseEntity.ok(publishingService.publishPhase(eventCode, PublishingPhase.fromValue(phase)));
    }

    @Override
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<UnpublishPhaseResponse> unpublishPhase(String eventCode, String phase) {
        return ResponseEntity.ok(publishingService.unpublishPhase(eventCode, PublishingPhase.fromValue(phase)));
    }

    @Override
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<PublishingStatusResponse> getPublishing(String eventCode) {
        return ResponseEntity.ok(publishingService.getPublishing(eventCode));
    }

    @Override
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<PublishingStatusResponse> patchPublishing(
            String eventCode, PatchPublishingRequest patchPublishingRequest) {
        return ResponseEntity.ok(publishingService.patchPublishing(eventCode, patchPublishingRequest));
    }

    /**
     * 409: unpublish only the current phase, never publish below it (Story 4.9b).
     */
    @ExceptionHandler(PublishingService.PhaseConflictException.class)
    public ResponseEntity<ErrorResponse> handlePhaseConflict(
            PublishingService.PhaseConflictException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ErrorResponse.builder()
                .timestamp(Instant.now())
                .path(request.getRequestURI())
                .status(HttpStatus.CONFLICT.value())
                .error("Conflict")
                .message(ex.getMessage())
                .correlationId(CorrelationIdGenerator.current())
                .severity("LOW")
                .build());
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
