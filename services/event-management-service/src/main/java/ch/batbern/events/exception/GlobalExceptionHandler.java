package ch.batbern.events.exception;

import ch.batbern.shared.dto.ErrorResponse;
import ch.batbern.shared.exception.InvalidStateTransitionException;
import ch.batbern.shared.exception.NotFoundException;
import ch.batbern.shared.exception.ValidationException;
import ch.batbern.shared.types.SpeakerWorkflowState;
import ch.batbern.shared.util.CorrelationIdGenerator;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mapping.PropertyReferenceException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Global Exception Handler for Event Management Service
 * Story 1.15a.1: Events API Consolidation
 *
 * Handles validation errors and converts them to appropriate HTTP responses.
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /**
     * Handle ValidationException (invalid filter/sort syntax)
     * Returns HTTP 400 Bad Request
     */
    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(
            ValidationException ex,
            HttpServletRequest request) {
        log.warn("Validation error: {}", ex.getMessage());

        ErrorResponse error = ErrorResponse.builder()
                .timestamp(Instant.now())
                .path(request.getRequestURI())
                .status(HttpStatus.BAD_REQUEST.value())
                .error("Bad Request")
                .message(ex.getMessage())
                .correlationId(CorrelationIdGenerator.generate())
                .severity("MEDIUM")
                .details(ex.getDetails())
                .build();

        return ResponseEntity.badRequest().body(error);
    }

    /**
     * Handle AlreadyRespondedException (speaker already responded to invitation)
     * Returns HTTP 409 Conflict
     * Story 6.2a: Invitation Response Portal - AC7
     */
    @ExceptionHandler(AlreadyRespondedException.class)
    public ResponseEntity<ErrorResponse> handleAlreadyRespondedException(
            AlreadyRespondedException ex,
            HttpServletRequest request) {
        log.warn("Already responded: {}", ex.getMessage());

        Map<String, Object> details = new HashMap<>();
        details.put("previousResponse", ex.getPreviousResponse().toString());
        if (ex.getRespondedAt() != null) {
            details.put("respondedAt", ex.getRespondedAt().toString());
        }

        ErrorResponse error = ErrorResponse.builder()
                .timestamp(Instant.now())
                .path(request.getRequestURI())
                .status(HttpStatus.CONFLICT.value())
                .error("Conflict")
                .message(ex.getMessage())
                .correlationId(CorrelationIdGenerator.generate())
                .severity("MEDIUM")
                .details(details)
                .build();

        return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
    }

    /**
     * Handle IncompleteProfileException — authenticated user tried to register
     * for an event but their profile is missing first or last name.
     *
     * Returns HTTP 409 Conflict with {@code code = "profile_incomplete"} and
     * details about which fields are missing. Frontend reads this to show an
     * inline profile-completion form and retry the registration.
     */
    @ExceptionHandler(IncompleteProfileException.class)
    public ResponseEntity<ErrorResponse> handleIncompleteProfileException(
            IncompleteProfileException ex,
            HttpServletRequest request) {
        log.info("Refused authenticated registration — profile incomplete: {}", ex.getMessage());

        Map<String, Object> details = new HashMap<>();
        details.put("code", "profile_incomplete");
        details.put("username", ex.getUsername());
        details.put("missingFirstName", ex.isMissingFirstName());
        details.put("missingLastName", ex.isMissingLastName());

        ErrorResponse error = ErrorResponse.builder()
                .timestamp(Instant.now())
                .path(request.getRequestURI())
                .status(HttpStatus.CONFLICT.value())
                .error("Conflict")
                .message("Profile incomplete — please add your first and last name before registering")
                .correlationId(CorrelationIdGenerator.generate())
                .severity("LOW")
                .details(details)
                .build();

        return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
    }

    /**
     * Handle PropertyReferenceException (invalid field name in sort)
     * Returns HTTP 400 Bad Request
     */
    @ExceptionHandler(PropertyReferenceException.class)
    public ResponseEntity<ErrorResponse> handlePropertyReferenceException(
            PropertyReferenceException ex,
            HttpServletRequest request) {
        log.warn("Invalid property reference: {}", ex.getMessage());

        Map<String, Object> details = new HashMap<>();
        details.put("propertyName", ex.getPropertyName());

        ErrorResponse error = ErrorResponse.builder()
                .timestamp(Instant.now())
                .path(request.getRequestURI())
                .status(HttpStatus.BAD_REQUEST.value())
                .error("Bad Request")
                .message("Invalid field name in sort or filter: " + ex.getPropertyName())
                .correlationId(CorrelationIdGenerator.generate())
                .severity("MEDIUM")
                .details(details)
                .build();

        return ResponseEntity.badRequest().body(error);
    }

    /**
     * Handle MethodArgumentNotValidException (Jakarta validation errors)
     * Returns HTTP 400 Bad Request
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentNotValidException(
            MethodArgumentNotValidException ex,
            HttpServletRequest request) {

        Map<String, Object> details = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error ->
            details.put(error.getField(), error.getDefaultMessage())
        );

        String errors = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining(", "));

        log.warn("Validation error: {}", errors);

        ErrorResponse error = ErrorResponse.builder()
                .timestamp(Instant.now())
                .path(request.getRequestURI())
                .status(HttpStatus.BAD_REQUEST.value())
                .error("Bad Request")
                .message("Validation failed: " + errors)
                .correlationId(CorrelationIdGenerator.generate())
                .severity("MEDIUM")
                .details(details)
                .build();

        return ResponseEntity.badRequest().body(error);
    }

    /**
     * Handle ConstraintViolationException (JPA/Hibernate entity validation errors)
     * Returns HTTP 400 Bad Request
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolationException(
            ConstraintViolationException ex,
            HttpServletRequest request) {

        Map<String, Object> details = new HashMap<>();
        for (ConstraintViolation<?> violation : ex.getConstraintViolations()) {
            String propertyPath = violation.getPropertyPath().toString();
            details.put(propertyPath, violation.getMessage());
        }

        String errors = ex.getConstraintViolations().stream()
                .map(violation -> violation.getPropertyPath() + ": " + violation.getMessage())
                .collect(Collectors.joining(", "));

        log.warn("Constraint violation: {}", errors);

        ErrorResponse error = ErrorResponse.builder()
                .timestamp(Instant.now())
                .path(request.getRequestURI())
                .status(HttpStatus.BAD_REQUEST.value())
                .error("Bad Request")
                .message("Validation failed: " + errors)
                .correlationId(CorrelationIdGenerator.generate())
                .severity("MEDIUM")
                .details(details)
                .build();

        return ResponseEntity.badRequest().body(error);
    }

    /**
     * Handle EventNotFoundException (event not found by ID)
     * Returns HTTP 404 Not Found
     */
    @ExceptionHandler(EventNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleEventNotFoundException(
            EventNotFoundException ex,
            HttpServletRequest request) {
        log.warn("Event not found: {}", ex.getMessage());

        ErrorResponse error = ErrorResponse.builder()
                .timestamp(Instant.now())
                .path(request.getRequestURI())
                .status(HttpStatus.NOT_FOUND.value())
                .error("Not Found")
                .message(ex.getMessage())
                .correlationId(CorrelationIdGenerator.generate())
                .severity("LOW")
                .build();

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }

    /**
     * Handle jakarta.persistence.EntityNotFoundException.
     * P2 (Story 11.C.2 review): ContentSubmissionService.submit throws this when speaker
     * or event lookups miss; without a dedicated handler it falls through to the generic
     * 500 handler. Map to 404 to match the javadoc contract on submit().
     */
    @ExceptionHandler(jakarta.persistence.EntityNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleEntityNotFoundException(
            jakarta.persistence.EntityNotFoundException ex,
            HttpServletRequest request) {
        log.warn("Entity not found: {}", ex.getMessage());

        ErrorResponse error = ErrorResponse.builder()
                .timestamp(Instant.now())
                .path(request.getRequestURI())
                .status(HttpStatus.NOT_FOUND.value())
                .error("Not Found")
                .message(ex.getMessage())
                .correlationId(CorrelationIdGenerator.generate())
                .severity("LOW")
                .build();

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }

    /**
     * Handle UserServiceException — cross-service HTTP failures from CUMS.
     * P2 (Story 11.C.2 review): when CUMS returns 400 (e.g., bio too long, blank field),
     * UserApiClientImpl re-throws as UserServiceException(status=400). Without a handler
     * this fell through to 500; map back to the originating status code so the speaker
     * sees the same 400 as the organizer endpoint would.
     */
    @ExceptionHandler(ch.batbern.events.exception.UserServiceException.class)
    public ResponseEntity<ErrorResponse> handleUserServiceException(
            ch.batbern.events.exception.UserServiceException ex,
            HttpServletRequest request) {
        Integer rawStatus = ex.getStatusCode();
        int status = (rawStatus != null && rawStatus >= 400 && rawStatus < 600)
                ? rawStatus
                : HttpStatus.BAD_GATEWAY.value();
        HttpStatus httpStatus = HttpStatus.valueOf(status);
        log.warn("User Management Service error: status={} message={}", status, ex.getMessage());

        ErrorResponse error = ErrorResponse.builder()
                .timestamp(Instant.now())
                .path(request.getRequestURI())
                .status(status)
                .error(httpStatus.getReasonPhrase())
                .message(ex.getMessage())
                .correlationId(CorrelationIdGenerator.generate())
                .severity(status >= 500 ? "HIGH" : "MEDIUM")
                .build();

        return ResponseEntity.status(httpStatus).body(error);
    }

    /**
     * Handle TopicNotFoundException (topic not found)
     * Returns HTTP 404 Not Found
     * Story 5.2: Topic Selection Workflow
     */
    @ExceptionHandler(TopicNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleTopicNotFoundException(
            TopicNotFoundException ex,
            HttpServletRequest request) {
        log.warn("Topic not found: {}", ex.getMessage());

        ErrorResponse error = ErrorResponse.builder()
                .timestamp(Instant.now())
                .path(request.getRequestURI())
                .status(HttpStatus.NOT_FOUND.value())
                .error("Not Found")
                .message(ex.getMessage())
                .correlationId(CorrelationIdGenerator.generate())
                .severity("LOW")
                .build();

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }

    /**
     * Handle SpeakerNotFoundException (speaker not found in speaker pool)
     * Returns HTTP 404 Not Found
     * Story 5.3: Speaker Outreach Tracking
     */
    @ExceptionHandler(SpeakerNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleSpeakerNotFoundException(
            SpeakerNotFoundException ex,
            HttpServletRequest request) {
        log.warn("Speaker not found: {}", ex.getMessage());

        ErrorResponse error = ErrorResponse.builder()
                .timestamp(Instant.now())
                .path(request.getRequestURI())
                .status(HttpStatus.NOT_FOUND.value())
                .error("Not Found")
                .message(ex.getMessage())
                .correlationId(CorrelationIdGenerator.generate())
                .severity("LOW")
                .build();

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }

    /**
     * Handle UserNotFoundException (user not found via API)
     * Returns HTTP 404 Not Found
     */
    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleUserNotFoundException(
            UserNotFoundException ex,
            HttpServletRequest request) {
        log.warn("User not found: {}", ex.getMessage());

        ErrorResponse error = ErrorResponse.builder()
                .timestamp(Instant.now())
                .path(request.getRequestURI())
                .status(HttpStatus.NOT_FOUND.value())
                .error("Not Found")
                .message(ex.getMessage())
                .correlationId(CorrelationIdGenerator.generate())
                .severity("LOW")
                .build();

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }

    /**
     * Handle SpeakerAssignmentNotFoundException (speaker assignment not found)
     * Returns HTTP 404 Not Found
     */
    @ExceptionHandler(SpeakerAssignmentNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleSpeakerAssignmentNotFoundException(
            SpeakerAssignmentNotFoundException ex,
            HttpServletRequest request) {
        log.warn("Speaker assignment not found: {}", ex.getMessage());

        ErrorResponse error = ErrorResponse.builder()
                .timestamp(Instant.now())
                .path(request.getRequestURI())
                .status(HttpStatus.NOT_FOUND.value())
                .error("Not Found")
                .message(ex.getMessage())
                .correlationId(CorrelationIdGenerator.generate())
                .severity("LOW")
                .build();

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }

    /**
     * Handle SessionNotFoundException (session not found by slug)
     * Returns HTTP 404 Not Found
     * Story BAT-11 (5.7): Slot Assignment & Progressive Publishing
     */
    @ExceptionHandler(SessionNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleSessionNotFoundException(
            SessionNotFoundException ex,
            HttpServletRequest request) {
        log.warn("Session not found: {}", ex.getMessage());

        ErrorResponse error = ErrorResponse.builder()
                .timestamp(Instant.now())
                .path(request.getRequestURI())
                .status(HttpStatus.NOT_FOUND.value())
                .error("Not Found")
                .message(ex.getMessage())
                .correlationId(CorrelationIdGenerator.generate())
                .severity("LOW")
                .build();

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }

    /**
     * Handle TeaserImageLimitExceededException (max teaser images reached)
     * Returns HTTP 422 Unprocessable Entity
     * Story 10.22: Event Teaser Images — AC6
     */
    @ExceptionHandler(TeaserImageLimitExceededException.class)
    public ResponseEntity<ErrorResponse> handleTeaserImageLimitExceededException(
            TeaserImageLimitExceededException ex,
            HttpServletRequest request) {
        log.warn("Teaser image limit exceeded: {}", ex.getMessage());

        ErrorResponse error = ErrorResponse.builder()
                .timestamp(Instant.now())
                .path(request.getRequestURI())
                .status(HttpStatus.UNPROCESSABLE_ENTITY.value())
                .error("Unprocessable Entity")
                .message(ex.getMessage())
                .correlationId(CorrelationIdGenerator.generate())
                .severity("LOW")
                .build();

        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(error);
    }

    /**
     * Handle InvalidFileTypeException (unsupported file type for photo upload)
     * Returns HTTP 422 Unprocessable Entity
     * Story 10.21: Event Photos Gallery
     */
    @ExceptionHandler(InvalidFileTypeException.class)
    public ResponseEntity<ErrorResponse> handleInvalidFileTypeException(
            InvalidFileTypeException ex,
            HttpServletRequest request) {
        log.warn("Invalid file type: {}", ex.getMessage());

        ErrorResponse error = ErrorResponse.builder()
                .timestamp(Instant.now())
                .path(request.getRequestURI())
                .status(HttpStatus.UNPROCESSABLE_ENTITY.value())
                .error("Unprocessable Entity")
                .message(ex.getMessage())
                .correlationId(CorrelationIdGenerator.generate())
                .severity("LOW")
                .build();

        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(error);
    }

    /**
     * Handle MaterialNotFoundException (session material not found)
     * Returns HTTP 404 Not Found
     *
     * Story 5.9: Session Materials Upload
     */
    @ExceptionHandler(MaterialNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleMaterialNotFoundException(
            MaterialNotFoundException ex,
            HttpServletRequest request) {
        log.warn("Material not found: {}", ex.getMessage());

        ErrorResponse error = ErrorResponse.builder()
                .timestamp(Instant.now())
                .path(request.getRequestURI())
                .status(HttpStatus.NOT_FOUND.value())
                .error("Not Found")
                .message(ex.getMessage())
                .correlationId(CorrelationIdGenerator.generate())
                .severity("LOW")
                .build();

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }

    /**
     * Handle NoSuchElementException (resource not found)
     * Returns HTTP 404 Not Found
     *
     * Story 2.2a: Anonymous Event Registration (ADR-005)
     */
    @ExceptionHandler(java.util.NoSuchElementException.class)
    public ResponseEntity<ErrorResponse> handleNoSuchElementException(
            java.util.NoSuchElementException ex,
            HttpServletRequest request) {
        log.warn("Resource not found: {}", ex.getMessage());

        ErrorResponse error = ErrorResponse.builder()
                .timestamp(Instant.now())
                .path(request.getRequestURI())
                .status(HttpStatus.NOT_FOUND.value())
                .error("Not Found")
                .message(ex.getMessage())
                .correlationId(CorrelationIdGenerator.generate())
                .severity("LOW")
                .build();

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }

    /**
     * Handle NotFoundException (generic not found from shared-kernel)
     * Returns HTTP 404 Not Found
     * Story 5.4: Speaker Status Management
     * Story 6.0a: Speaker Workflow State Machine Foundation
     */
    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFoundException(
            NotFoundException ex,
            HttpServletRequest request) {
        log.warn("Resource not found: {}", ex.getMessage());

        ErrorResponse error = ErrorResponse.builder()
                .timestamp(Instant.now())
                .path(request.getRequestURI())
                .status(HttpStatus.NOT_FOUND.value())
                .error("Not Found")
                .message(ex.getMessage())
                .correlationId(CorrelationIdGenerator.generate())
                .severity("LOW")
                .build();

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }

    /**
     * Handle IllegalArgumentException (invalid input or business logic constraint)
     * Returns HTTP 400 Bad Request
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgumentException(
            IllegalArgumentException ex,
            HttpServletRequest request) {
        log.warn("Invalid argument: {}", ex.getMessage());

        ErrorResponse error = ErrorResponse.builder()
                .timestamp(Instant.now())
                .path(request.getRequestURI())
                .status(HttpStatus.BAD_REQUEST.value())
                .error("Bad Request")
                .message(ex.getMessage())
                .correlationId(CorrelationIdGenerator.generate())
                .severity("MEDIUM")
                .build();

        return ResponseEntity.badRequest().body(error);
    }

    /**
     * Handle IllegalStateException (business logic constraint violations)
     * Returns HTTP 409 Conflict
     * QA Fix (VALID-001): Handle duplicate registration attempts
     */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorResponse> handleIllegalStateException(
            IllegalStateException ex,
            HttpServletRequest request) {
        log.warn("Illegal state: {}", ex.getMessage());

        ErrorResponse error = ErrorResponse.builder()
                .timestamp(Instant.now())
                .path(request.getRequestURI())
                .status(HttpStatus.CONFLICT.value())
                .error("Conflict")
                .message(ex.getMessage())
                .correlationId(CorrelationIdGenerator.generate())
                .severity("MEDIUM")
                .build();

        return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
    }

    /**
     * Handle BusinessValidationException (business logic validation failures)
     * Returns HTTP 422 Unprocessable Entity with VALIDATION_ERROR code
     */
    @ExceptionHandler(BusinessValidationException.class)
    public ResponseEntity<ErrorResponse> handleBusinessValidationException(
            BusinessValidationException ex,
            HttpServletRequest request) {
        log.warn("Business validation error: {}", ex.getMessage());

        ErrorResponse error = ErrorResponse.builder()
                .timestamp(Instant.now())
                .path(request.getRequestURI())
                .status(HttpStatus.UNPROCESSABLE_ENTITY.value())
                .error("Unprocessable Entity")
                .message(ex.getMessage())
                .correlationId(CorrelationIdGenerator.generate())
                .severity("MEDIUM")
                .build();

        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(error);
    }

    /**
     * Handle AccessDeniedException (Spring Security access denied)
     * Returns HTTP 403 Forbidden
     * Story 5.9: Session Materials Upload (AC7 - RBAC enforcement)
     */
    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDeniedException(
            org.springframework.security.access.AccessDeniedException ex,
            HttpServletRequest request) {
        log.warn("Access denied: {}", ex.getMessage());

        ErrorResponse error = ErrorResponse.builder()
                .timestamp(Instant.now())
                .path(request.getRequestURI())
                .status(HttpStatus.FORBIDDEN.value())
                .error("Forbidden")
                .message(ex.getMessage())
                .correlationId(CorrelationIdGenerator.generate())
                .severity("MEDIUM")
                .build();

        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error);
    }

    /**
     * Handle SpeakerPortalAccessDeniedException — Story 11.E.3.
     * Returns HTTP 403 Forbidden when a speaker tries to act on a pool row they don't own.
     * The message is sanitised: the offending eventCode is logged but not echoed to the
     * client (already in the path; no extra info exposure).
     */
    @ExceptionHandler(SpeakerPortalAccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleSpeakerPortalAccessDenied(
            SpeakerPortalAccessDeniedException ex,
            HttpServletRequest request) {
        // Code review 2026-05-18 (P21): generate correlation ID once so log + response share it,
        // letting support tie the WARN line above to the 403 the user reported.
        String correlationId = CorrelationIdGenerator.generate();
        log.warn("Speaker portal access denied: path={} correlationId={} reason={}",
                request.getRequestURI(), correlationId, ex.getMessage());

        ErrorResponse error = ErrorResponse.builder()
                .timestamp(Instant.now())
                .path(request.getRequestURI())
                .status(HttpStatus.FORBIDDEN.value())
                .error("Forbidden")
                .message(ex.getMessage())
                .correlationId(correlationId)
                .severity("MEDIUM")
                .build();

        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error);
    }

    /**
     * Handle AuthorizationDeniedException (access denied)
     * Returns HTTP 403 Forbidden
     * Story 5.1: Event Type Definition (AC8 - ORGANIZER role required)
     */
    @ExceptionHandler(AuthorizationDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAuthorizationDeniedException(
            AuthorizationDeniedException ex,
            HttpServletRequest request) {
        log.warn("Authorization denied: {}", ex.getMessage());

        ErrorResponse error = ErrorResponse.builder()
                .timestamp(Instant.now())
                .path(request.getRequestURI())
                .status(HttpStatus.FORBIDDEN.value())
                .error("Forbidden")
                .message("Access denied - insufficient permissions")
                .correlationId(CorrelationIdGenerator.generate())
                .severity("MEDIUM")
                .build();

        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error);
    }

    /**
     * Handle WorkflowException (invalid workflow state transition)
     * Returns HTTP 422 Unprocessable Entity with WORKFLOW_ERROR code
     */
    @ExceptionHandler(WorkflowException.class)
    public ResponseEntity<ErrorResponse> handleWorkflowException(
            WorkflowException ex,
            HttpServletRequest request) {
        log.warn("Workflow error: {}", ex.getMessage());

        ErrorResponse error = ErrorResponse.builder()
                .timestamp(Instant.now())
                .path(request.getRequestURI())
                .status(HttpStatus.UNPROCESSABLE_ENTITY.value())
                .error("Unprocessable Entity")
                .message(ex.getMessage())
                .correlationId(CorrelationIdGenerator.generate())
                .severity("HIGH")
                .build();

        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(error);
    }

    /**
     * Handle InvalidStateTransitionException (invalid workflow state transition attempt)
     * Returns HTTP 422 Unprocessable Entity
     * Story 5.1a: Workflow State Machine Foundation - AC12
     * Story 5.4: Speaker Status Management - AC12 (cannot un-accept)
     */
    @ExceptionHandler(InvalidStateTransitionException.class)
    public ResponseEntity<ErrorResponse> handleInvalidStateTransitionException(
            InvalidStateTransitionException ex,
            HttpServletRequest request) {
        log.warn("Invalid state transition: {}", ex.getMessage());

        ErrorResponse error = ErrorResponse.builder()
                .timestamp(Instant.now())
                .path(request.getRequestURI())
                .status(HttpStatus.UNPROCESSABLE_ENTITY.value())
                .error("InvalidStateTransitionException")
                .message(ex.getMessage())
                .correlationId(CorrelationIdGenerator.generate())
                .severity("MEDIUM")
                .build();

        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(error);
    }

    /**
     * Handle WorkflowValidationException (business rule validation failure for workflow transition)
     * Returns HTTP 422 Unprocessable Entity
     * Story 5.1a: Workflow State Machine Foundation - AC12
     */
    @ExceptionHandler(WorkflowValidationException.class)
    public ResponseEntity<ErrorResponse> handleWorkflowValidationException(
            WorkflowValidationException ex,
            HttpServletRequest request) {
        log.warn("Workflow validation error: {}", ex.getMessage());

        ErrorResponse error = ErrorResponse.builder()
                .timestamp(Instant.now())
                .path(request.getRequestURI())
                .status(HttpStatus.UNPROCESSABLE_ENTITY.value())
                .error("Unprocessable Entity")
                .message(ex.getMessage())
                .correlationId(CorrelationIdGenerator.generate())
                .severity("HIGH")
                .details(ex.getContext())
                .build();

        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(error);
    }

    /**
     * Handle MethodArgumentTypeMismatchException (invalid UUID format in path variable)
     * Returns HTTP 404 Not Found
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentTypeMismatchException(
            MethodArgumentTypeMismatchException ex,
            HttpServletRequest request) {
        log.warn("Invalid argument type: {}", ex.getMessage());

        Map<String, Object> details = new HashMap<>();
        details.put("parameterName", ex.getName());
        details.put("requiredType", ex.getRequiredType() != null ? ex.getRequiredType().getSimpleName() : "unknown");

        ErrorResponse error = ErrorResponse.builder()
                .timestamp(Instant.now())
                .path(request.getRequestURI())
                .status(HttpStatus.NOT_FOUND.value())
                .error("Not Found")
                .message("Invalid ID format")
                .correlationId(CorrelationIdGenerator.generate())
                .severity("LOW")
                .details(details)
                .build();

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }

    /**
     * Handle HttpMessageNotReadableException (JSON parsing errors, including invalid UUID format in request body)
     * Returns HTTP 400 Bad Request.
     *
     * <p>Story 11.B.3 AC4: when the underlying cause is an {@link InvalidFormatException}
     * whose target type is {@link SpeakerWorkflowState}, return a structured
     * {@code INVALID_SPEAKER_WORKFLOW_STATE} body listing the 8 accepted enum values.
     * Jackson rejects the 5 removed legacy values (SLOT_ASSIGNED, CONFIRMED, OVERFLOW,
     * WITHDREW, TENTATIVE) at request-body binding time — before the controller method
     * runs — so this handler is the only path that surfaces those rejections.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleHttpMessageNotReadableException(
            HttpMessageNotReadableException ex,
            HttpServletRequest request) {
        log.warn("Invalid request body: {}", ex.getMessage());

        // Story 11.B.3 AC4: detect SpeakerWorkflowState enum rejection at the deserialization layer.
        Throwable cause = ex.getCause();
        if (cause instanceof InvalidFormatException ife
                && ife.getTargetType() != null
                && ife.getTargetType().equals(SpeakerWorkflowState.class)) {
            String rejectedValue = ife.getValue() != null ? ife.getValue().toString() : "(null)";
            List<String> acceptedValues = Arrays.stream(SpeakerWorkflowState.values())
                    .map(Enum::name)
                    .collect(Collectors.toList());

            Map<String, Object> details = new HashMap<>();
            details.put("code", "INVALID_SPEAKER_WORKFLOW_STATE");
            details.put("rejectedValue", rejectedValue);
            details.put("acceptedValues", acceptedValues);

            String message = "Invalid speaker workflow state '" + rejectedValue
                    + "'. Accepted values: "
                    + acceptedValues.stream().collect(Collectors.joining(", "))
                    + ".";

            ErrorResponse error = ErrorResponse.builder()
                    .timestamp(Instant.now())
                    .path(request.getRequestURI())
                    .status(HttpStatus.BAD_REQUEST.value())
                    .error("Bad Request")
                    .message(message)
                    .correlationId(CorrelationIdGenerator.generate())
                    .severity("MEDIUM")
                    .details(details)
                    .build();

            return ResponseEntity.badRequest().body(error);
        }

        String message = "Invalid request format";
        // Check if it's a UUID parsing error
        if (cause != null && cause.getMessage() != null) {
            String causeMessage = cause.getMessage();
            if (causeMessage.contains("UUID")) {
                message = "Invalid UUID format in request";
            }
        }

        ErrorResponse error = ErrorResponse.builder()
                .timestamp(Instant.now())
                .path(request.getRequestURI())
                .status(HttpStatus.BAD_REQUEST.value())
                .error("Bad Request")
                .message(message)
                .correlationId(CorrelationIdGenerator.generate())
                .severity("MEDIUM")
                .build();

        return ResponseEntity.badRequest().body(error);
    }

    /**
     * Handle ReadyRequiresPromoteException — caller attempted to PUT /status with
     * newStatus = READY, but READY requires an email payload (User provisioning) and is
     * reachable only via POST /promote (Story 11.D.1).
     * Returns HTTP 400 Bad Request with code READY_REQUIRES_PROMOTE_ENDPOINT.
     * Story 11.B.3 AC5.
     */
    @ExceptionHandler(ReadyRequiresPromoteException.class)
    public ResponseEntity<ErrorResponse> handleReadyRequiresPromoteException(
            ReadyRequiresPromoteException ex,
            HttpServletRequest request) {
        log.warn("READY requires promote endpoint: {}", ex.getMessage());

        Map<String, Object> details = new HashMap<>();
        details.put("code", "READY_REQUIRES_PROMOTE_ENDPOINT");
        details.put("rejectedValue", "READY");
        details.put("alternativeEndpoint", "POST /api/v1/events/{eventCode}/speakers/{speakerId}/promote");

        ErrorResponse error = ErrorResponse.builder()
                .timestamp(Instant.now())
                .path(request.getRequestURI())
                .status(HttpStatus.BAD_REQUEST.value())
                .error("Bad Request")
                .message(ex.getMessage())
                .correlationId(CorrelationIdGenerator.generate())
                .severity("MEDIUM")
                .details(details)
                .build();

        return ResponseEntity.badRequest().body(error);
    }

    /**
     * Handle ObjectOptimisticLockingFailureException (concurrent modification of same entity)
     * Returns HTTP 409 Conflict
     *
     * This occurs when multiple requests try to modify the same entity simultaneously.
     * Common causes:
     * - Frontend making duplicate API calls (e.g., auto-save + manual save)
     * - User double-clicking submit button
     * - Multiple browser tabs modifying same resource
     *
     * Story bugfix: Workflow transitions were being triggered by BOTH auto-save and manual save,
     * causing race conditions. Frontend fix excludes workflow transitions from auto-save.
     */
    @ExceptionHandler(org.springframework.orm.ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> handleObjectOptimisticLockingFailureException(
            org.springframework.orm.ObjectOptimisticLockingFailureException ex,
            HttpServletRequest request) {
        log.warn("Concurrent modification detected: {}", ex.getMessage());

        ErrorResponse error = ErrorResponse.builder()
                .timestamp(Instant.now())
                .path(request.getRequestURI())
                .status(HttpStatus.CONFLICT.value())
                .error("Conflict")
                .message("The resource was modified by another request. Please refresh and try again.")
                .correlationId(CorrelationIdGenerator.generate())
                .severity("MEDIUM")
                .build();

        return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
    }

    /**
     * Handle generic exceptions
     * Returns HTTP 500 Internal Server Error
     */
    /**
     * Handle StructuralSessionsAlreadyExistException (structural sessions already generated)
     * Returns HTTP 409 Conflict
     */
    @ExceptionHandler(StructuralSessionsAlreadyExistException.class)
    public ResponseEntity<ErrorResponse> handleStructuralSessionsAlreadyExistException(
            StructuralSessionsAlreadyExistException ex,
            HttpServletRequest request) {
        log.warn("Structural sessions already exist: {}", ex.getMessage());

        ErrorResponse error = ErrorResponse.builder()
                .timestamp(Instant.now())
                .path(request.getRequestURI())
                .status(HttpStatus.CONFLICT.value())
                .error("Conflict")
                .message(ex.getMessage())
                .correlationId(CorrelationIdGenerator.generate())
                .severity("MEDIUM")
                .build();

        return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
    }

    /**
     * Handle InvalidPromotionStateException (Story 11.D.1):
     * {@code POST /speakers/{id}/promote} called on a speaker whose current state is not
     * {@code CONTACTED} or {@code READY}. Returns HTTP 409 Conflict with
     * {@code details.code = INVALID_PROMOTION_STATE} and {@code details.currentState =
     * <state>} so the frontend can render a tailored message.
     */
    @ExceptionHandler(InvalidPromotionStateException.class)
    public ResponseEntity<ErrorResponse> handleInvalidPromotionStateException(
            InvalidPromotionStateException ex,
            HttpServletRequest request) {
        log.warn("Invalid promotion state: {}", ex.getMessage());

        Map<String, Object> details = new HashMap<>();
        details.put("code", "INVALID_PROMOTION_STATE");
        details.put("currentState", ex.getCurrentState().name());

        ErrorResponse error = ErrorResponse.builder()
                .timestamp(Instant.now())
                .path(request.getRequestURI())
                .status(HttpStatus.CONFLICT.value())
                .error("Conflict")
                .message(ex.getMessage())
                .correlationId(CorrelationIdGenerator.generate())
                .severity("MEDIUM")
                .details(details)
                .build();

        return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
    }

    /**
     * Handle SlotCapacityReachedException (READY → INVITED blocked by slot-capacity gate)
     * Returns HTTP 409 Conflict.
     * Story 11.B.2: slot-capacity gate replaces removed OVERFLOW state (ADR-009 §0.7).
     */
    @ExceptionHandler(SlotCapacityReachedException.class)
    public ResponseEntity<ErrorResponse> handleSlotCapacityReachedException(
            SlotCapacityReachedException ex,
            HttpServletRequest request) {
        log.warn("Slot capacity reached: {}", ex.getMessage());

        Map<String, Object> details = new HashMap<>();
        details.put("code", "SLOT_CAPACITY_REACHED");
        details.put("eventId", ex.getEventId().toString());
        details.put("acceptedCount", ex.getAcceptedCount());
        details.put("invitedCount", ex.getInvitedCount());
        details.put("maxSlots", ex.getMaxSlots());

        ErrorResponse error = ErrorResponse.builder()
                .timestamp(Instant.now())
                .path(request.getRequestURI())
                .status(HttpStatus.CONFLICT.value())
                .error("Conflict")
                .message(ex.getMessage())
                .correlationId(CorrelationIdGenerator.generate())
                .severity("MEDIUM")
                .details(details)
                .build();

        return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
    }

    /**
     * Handle DuplicateSubscriberException (email already subscribed to newsletter)
     * Returns HTTP 409 Conflict
     */
    @ExceptionHandler(DuplicateSubscriberException.class)
    public ResponseEntity<ErrorResponse> handleDuplicateSubscriberException(
            DuplicateSubscriberException ex,
            HttpServletRequest request) {
        log.warn("Duplicate newsletter subscriber: {}", ex.getMessage());

        ErrorResponse error = ErrorResponse.builder()
                .timestamp(Instant.now())
                .path(request.getRequestURI())
                .status(HttpStatus.CONFLICT.value())
                .error("Conflict")
                .message(ex.getMessage())
                .correlationId(CorrelationIdGenerator.generate())
                .severity("LOW")
                .build();

        return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
    }

    /**
     * Handle ReservedEmailDomainException — newsletter subscribe attempted with an
     * RFC 2606 / RFC 6761 reserved domain (e.g. example.com, *.test, *.invalid).
     * Returns HTTP 400 Bad Request.
     */
    @ExceptionHandler(ReservedEmailDomainException.class)
    public ResponseEntity<ErrorResponse> handleReservedEmailDomainException(
            ReservedEmailDomainException ex,
            HttpServletRequest request) {
        log.warn("Rejected newsletter subscribe — reserved domain: {}", ex.getMessage());

        ErrorResponse error = ErrorResponse.builder()
                .timestamp(Instant.now())
                .path(request.getRequestURI())
                .status(HttpStatus.BAD_REQUEST.value())
                .error("Bad Request")
                .message(ex.getMessage())
                .correlationId(CorrelationIdGenerator.generate())
                .severity("LOW")
                .build();

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    /**
     * Handle DuplicateNewsletterSendException (send already in progress for the same event).
     * Returns HTTP 409 Conflict.
     */
    @ExceptionHandler(DuplicateNewsletterSendException.class)
    public ResponseEntity<ErrorResponse> handleDuplicateNewsletterSendException(
            DuplicateNewsletterSendException ex,
            HttpServletRequest request) {
        log.warn("Duplicate newsletter send attempt: {}", ex.getMessage());

        ErrorResponse error = ErrorResponse.builder()
                .timestamp(Instant.now())
                .path(request.getRequestURI())
                .status(HttpStatus.CONFLICT.value())
                .error("Conflict")
                .message(ex.getMessage())
                .correlationId(CorrelationIdGenerator.generate())
                .severity("LOW")
                .build();

        return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
    }

    /**
     * Spring MVC raises {@code NoHandlerFoundException} / {@code NoResourceFoundException}
     * when no controller method matches the inbound request. Without this explicit handler
     * the catch-all {@code Exception} branch below would translate either into a 500 (same
     * trap as the project-context.md {@code MethodArgumentNotValidException} gotcha).
     * Story 11.F.1 added this handler so the post-teardown speaker-magic-login /
     * validate-token endpoints return a clean 404.
     */
    @ExceptionHandler({
        org.springframework.web.servlet.NoHandlerFoundException.class,
        org.springframework.web.servlet.resource.NoResourceFoundException.class
    })
    public ResponseEntity<ErrorResponse> handleNoHandlerFoundException(
            Exception ex,
            HttpServletRequest request) {
        log.debug("No handler for {} {}: {}",
                request.getMethod(), request.getRequestURI(), ex.getMessage());

        ErrorResponse error = ErrorResponse.builder()
                .timestamp(Instant.now())
                .path(request.getRequestURI())
                .status(HttpStatus.NOT_FOUND.value())
                .error("Not Found")
                .message("No endpoint " + request.getMethod() + " " + request.getRequestURI())
                .correlationId(CorrelationIdGenerator.generate())
                .severity("LOW")
                .build();

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }

    /**
     * Handle Spring's {@link org.springframework.web.server.ResponseStatusException} explicitly so
     * it isn't swallowed by the generic {@code @ExceptionHandler(Exception.class)} below (which
     * would otherwise turn an intentional 400 into a 500). Same class of gotcha called out in
     * {@code _bmad-output/project-context.md} for {@code MethodArgumentNotValidException}.
     */
    @ExceptionHandler(org.springframework.web.server.ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleResponseStatusException(
            org.springframework.web.server.ResponseStatusException ex,
            HttpServletRequest request) {
        log.warn("Response status exception: {} - {}", ex.getStatusCode(), ex.getReason());
        ErrorResponse error = ErrorResponse.builder()
                .timestamp(Instant.now())
                .path(request.getRequestURI())
                .status(ex.getStatusCode().value())
                .error(HttpStatus.valueOf(ex.getStatusCode().value()).getReasonPhrase())
                .message(ex.getReason() != null ? ex.getReason() : "Request rejected")
                .correlationId(CorrelationIdGenerator.generate())
                .severity("WARNING")
                .build();
        return ResponseEntity.status(ex.getStatusCode()).body(error);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(
            Exception ex,
            HttpServletRequest request) {
        log.error("Unexpected error", ex);

        ErrorResponse error = ErrorResponse.builder()
                .timestamp(Instant.now())
                .path(request.getRequestURI())
                .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                .error("Internal Server Error")
                .message("An unexpected error occurred")
                .correlationId(CorrelationIdGenerator.generate())
                .severity("CRITICAL")
                .build();

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }
}
