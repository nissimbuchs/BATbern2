package ch.batbern.events.exception;

import ch.batbern.shared.dto.ErrorResponse;
import ch.batbern.shared.exception.ClientErrorClassifier;
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
import org.springframework.data.core.PropertyReferenceException;
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
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Global Exception Handler for Event Management Service (platform, L0).
 *
 * <p>Maps the platform cases: Spring MVC, Spring Security, JPA, the shared-kernel exceptions and the
 * catch-all. EMS domain exceptions are mapped once through their base {@link DomainException}; it uses
 * no other EMS class ({@code EmsExceptionMappingTest}, Story 17.14). A module whose exception needs
 * extra response fields has its own advice ({@code SpeakerExceptionHandler},
 * {@code RegistrationExceptionHandler}, {@code EventWorkflowExceptionHandler},
 * {@code mailcenter/MailCenterExceptionHandler}).
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
                .correlationId(CorrelationIdGenerator.current())
                .severity("MEDIUM")
                .details(ex.getDetails())
                .build();

        return ResponseEntity.badRequest().body(error);
    }

    /**
     * Every EMS domain exception (Story 17.14): status, severity and {@code details.code} come from the
     * exception, so a new exception needs no handler here. One whose body carries more fields has a
     * handler in its module's advice ({@code @Order(HIGHEST_PRECEDENCE)}), which runs first.
     */
    @ExceptionHandler(DomainException.class)
    public ResponseEntity<ErrorResponse> handleDomainException(
            DomainException ex,
            HttpServletRequest request) {
        log.warn("Domain exception {} [correlationId={}] {} {}: {}", ex.getStatus().value(),
                CorrelationIdGenerator.current(), request.getRequestURI(), ex.getClass().getSimpleName(),
                ex.getMessage());
        return DomainErrorResponse.of(ex, request);
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
                .correlationId(CorrelationIdGenerator.current())
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
                .correlationId(CorrelationIdGenerator.current())
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
                .correlationId(CorrelationIdGenerator.current())
                .severity("MEDIUM")
                .details(details)
                .build();

        return ResponseEntity.badRequest().body(error);
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
                .correlationId(CorrelationIdGenerator.current())
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
                .correlationId(CorrelationIdGenerator.current())
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
                .correlationId(CorrelationIdGenerator.current())
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
                .correlationId(CorrelationIdGenerator.current())
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
                .correlationId(CorrelationIdGenerator.current())
                .severity("MEDIUM")
                .build();

        return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
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
                .correlationId(CorrelationIdGenerator.current())
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
                .correlationId(CorrelationIdGenerator.current())
                .severity("MEDIUM")
                .build();

        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error);
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
                .correlationId(CorrelationIdGenerator.current())
                .severity("MEDIUM")
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
                .correlationId(CorrelationIdGenerator.current())
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
                    .correlationId(CorrelationIdGenerator.current())
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
                .correlationId(CorrelationIdGenerator.current())
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
                .correlationId(CorrelationIdGenerator.current())
                .severity("MEDIUM")
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
                .correlationId(CorrelationIdGenerator.current())
                .severity("LOW")
                .build();

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }

    /**
     * Returns 405 Method Not Allowed when a URI matches a registered route but the HTTP verb
     * doesn't (e.g. POST /event-types when only GET is mapped). Without this explicit handler
     * Spring raises {@code HttpRequestMethodNotSupportedException}, which falls through to the
     * catch-all {@code @ExceptionHandler(Exception.class)} below and gets translated into a 500 —
     * the same class of gotcha as the {@code MethodArgumentNotValidException} rule in
     * {@code _bmad-output/project-context.md}. Discovered via PR 2a's event-types-api tests
     * 08/09 which document the no-POST/no-DELETE API gap.
     */
    @ExceptionHandler(org.springframework.web.HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotSupported(
            org.springframework.web.HttpRequestMethodNotSupportedException ex,
            HttpServletRequest request) {
        log.debug("Method not supported for {} {}: {}",
                request.getMethod(), request.getRequestURI(), ex.getMessage());

        ErrorResponse error = ErrorResponse.builder()
                .timestamp(Instant.now())
                .path(request.getRequestURI())
                .status(HttpStatus.METHOD_NOT_ALLOWED.value())
                .error("Method Not Allowed")
                .message(ex.getMessage())
                .correlationId(CorrelationIdGenerator.current())
                .severity("LOW")
                .build();

        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(error);
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
                .correlationId(CorrelationIdGenerator.current())
                .severity("LOW")
                .build();
        return ResponseEntity.status(ex.getStatusCode()).body(error);
    }

    /**
     * Catch-all: a client mistake no handler maps answers its 4xx ({@link ClientErrorClassifier}),
     * anything else 500 CRITICAL.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(
            Exception ex,
            HttpServletRequest request) {
        // Obtain the correlation ID BEFORE logging, and log it, so the ID handed back to
        // the caller can actually be found in CloudWatch (issue #904). Generating it after
        // the log call — as this did — returns the client an ID that appears in no log line.
        String correlationId = CorrelationIdGenerator.current();
        // A client mistake that no specific handler maps (missing parameter/header, 415, 404,
        // NUL byte) is a 4xx, not a 500 CRITICAL (#1148, #1157, #1103).
        Optional<ClientErrorClassifier.ClientError> clientError = ClientErrorClassifier.classify(ex);
        if (clientError.isPresent()) {
            log.warn("Client error {} [correlationId={}] {} {}: {}", clientError.get().status().value(),
                    correlationId, request.getMethod(), request.getRequestURI(), ex.getMessage());
            return clientError.get().toResponseEntity(request.getRequestURI(), correlationId);
        }
        log.error("Unexpected error [correlationId={}]", correlationId, ex);

        ErrorResponse error = ErrorResponse.builder()
                .timestamp(Instant.now())
                .path(request.getRequestURI())
                .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                .error("Internal Server Error")
                .message("An unexpected error occurred")
                .correlationId(correlationId)
                .severity("CRITICAL")
                .build();

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }
}
