package ch.batbern.events.exception;

import ch.batbern.shared.dto.ErrorResponse;
import ch.batbern.shared.exception.InvalidStateTransitionException;
import ch.batbern.shared.exception.NotFoundException;
import ch.batbern.shared.exception.ValidationException;
import ch.batbern.shared.util.CorrelationIdGenerator;
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
import java.util.HashMap;
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
     * Handle InvalidTokenException (magic link token invalid/expired/used)
     * Returns HTTP 401 Unauthorized
     * Story 6.2a: Invitation Response Portal
     */
    @ExceptionHandler(InvalidTokenException.class)
    public ResponseEntity<ErrorResponse> handleInvalidTokenException(
            InvalidTokenException ex,
            HttpServletRequest request) {
        log.warn("Invalid token: {} - {}", ex.getErrorCode(), ex.getMessage());

        Map<String, Object> details = new HashMap<>();
        details.put("errorCode", ex.getErrorCode());

        ErrorResponse error = ErrorResponse.builder()
                .timestamp(Instant.now())
                .path(request.getRequestURI())
                .status(HttpStatus.UNAUTHORIZED.value())
                .error("Unauthorized")
                .message(ex.getMessage())
                .correlationId(CorrelationIdGenerator.generate())
                .severity("MEDIUM")
                .details(details)
                .build();

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
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
     * Returns HTTP 400 Bad Request
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleHttpMessageNotReadableException(
            HttpMessageNotReadableException ex,
            HttpServletRequest request) {
        log.warn("Invalid request body: {}", ex.getMessage());

        String message = "Invalid request format";
        // Check if it's a UUID parsing error
        if (ex.getCause() != null && ex.getCause().getMessage() != null) {
            String causeMessage = ex.getCause().getMessage();
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
