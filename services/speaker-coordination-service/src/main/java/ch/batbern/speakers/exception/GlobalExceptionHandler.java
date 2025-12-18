package ch.batbern.speakers.exception;

import ch.batbern.shared.dto.ErrorResponse;
import ch.batbern.shared.exception.InvalidStateTransitionException;
import ch.batbern.shared.exception.NotFoundException;
import ch.batbern.shared.exception.ValidationException;
import ch.batbern.shared.util.CorrelationIdGenerator;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Global Exception Handler for Speaker Coordination Service
 * Story 5.4: Speaker Status Management
 *
 * Handles validation errors and converts them to appropriate HTTP responses.
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /**
     * Handle InvalidStateTransitionException (AC12)
     * Returns HTTP 422 Unprocessable Entity
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
                .severity(ex.getSeverity().toString())
                .details(ex.getDetails())
                .build();

        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(error);
    }

    /**
     * Handle NotFoundException
     * Returns HTTP 404 Not Found
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
                .severity(ex.getSeverity().toString())
                .details(ex.getDetails())
                .build();

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }

    /**
     * Handle ValidationException (invalid request)
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
                .severity(ex.getSeverity().toString())
                .details(ex.getDetails())
                .build();

        return ResponseEntity.badRequest().body(error);
    }

    /**
     * Handle MethodArgumentNotValidException (@Valid validation failure)
     * Returns HTTP 400 Bad Request
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentNotValidException(
            MethodArgumentNotValidException ex,
            HttpServletRequest request) {
        log.warn("Validation error: {}", ex.getMessage());

        Map<String, Object> details = new HashMap<>();
        details.put("validationErrors", ex.getBindingResult().getFieldErrors().stream()
                .map(error -> Map.of(
                        "field", error.getField(),
                        "message", error.getDefaultMessage() != null ? error.getDefaultMessage() : "Invalid value",
                        "rejectedValue", error.getRejectedValue() != null ? error.getRejectedValue().toString() : "null"
                ))
                .collect(Collectors.toList()));

        ErrorResponse error = ErrorResponse.builder()
                .timestamp(Instant.now())
                .path(request.getRequestURI())
                .status(HttpStatus.BAD_REQUEST.value())
                .error("Bad Request")
                .message("Validation failed: " + ex.getBindingResult().getFieldErrors().stream()
                        .map(fieldError -> fieldError.getDefaultMessage())
                        .collect(Collectors.joining(", ")))
                .correlationId(CorrelationIdGenerator.generate())
                .severity("MEDIUM")
                .details(details)
                .build();

        return ResponseEntity.badRequest().body(error);
    }

    /**
     * Handle ConstraintViolationException (Bean validation)
     * Returns HTTP 400 Bad Request
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolationException(
            ConstraintViolationException ex,
            HttpServletRequest request) {
        log.warn("Constraint violation: {}", ex.getMessage());

        Map<String, Object> details = new HashMap<>();
        details.put("violations", ex.getConstraintViolations().stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.toList()));

        ErrorResponse error = ErrorResponse.builder()
                .timestamp(Instant.now())
                .path(request.getRequestURI())
                .status(HttpStatus.BAD_REQUEST.value())
                .error("Bad Request")
                .message("Validation failed: " + ex.getMessage())
                .correlationId(CorrelationIdGenerator.generate())
                .severity("MEDIUM")
                .details(details)
                .build();

        return ResponseEntity.badRequest().body(error);
    }

    /**
     * Handle HttpMessageNotReadableException (malformed JSON)
     * Returns HTTP 400 Bad Request
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleHttpMessageNotReadableException(
            HttpMessageNotReadableException ex,
            HttpServletRequest request) {
        log.warn("Malformed request body: {}", ex.getMessage());

        ErrorResponse error = ErrorResponse.builder()
                .timestamp(Instant.now())
                .path(request.getRequestURI())
                .status(HttpStatus.BAD_REQUEST.value())
                .error("Bad Request")
                .message("Malformed JSON request body")
                .correlationId(CorrelationIdGenerator.generate())
                .severity("MEDIUM")
                .build();

        return ResponseEntity.badRequest().body(error);
    }

    /**
     * Handle AuthorizationDeniedException (Spring Security 6+)
     * Returns HTTP 403 Forbidden
     */
    @ExceptionHandler({AuthorizationDeniedException.class, AccessDeniedException.class})
    public ResponseEntity<ErrorResponse> handleAccessDeniedException(
            Exception ex,
            HttpServletRequest request) {
        log.warn("Access denied: {}", ex.getMessage());

        ErrorResponse error = ErrorResponse.builder()
                .timestamp(Instant.now())
                .path(request.getRequestURI())
                .status(HttpStatus.FORBIDDEN.value())
                .error("Forbidden")
                .message("Access denied - insufficient permissions")
                .correlationId(CorrelationIdGenerator.generate())
                .severity("HIGH")
                .build();

        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error);
    }

    /**
     * Handle all other exceptions
     * Returns HTTP 500 Internal Server Error
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(
            Exception ex,
            HttpServletRequest request) {
        log.error("Unexpected error: {}", ex.getMessage(), ex);

        ErrorResponse error = ErrorResponse.builder()
                .timestamp(Instant.now())
                .path(request.getRequestURI())
                .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                .error("Internal Server Error")
                .message("An unexpected error occurred")
                .correlationId(CorrelationIdGenerator.generate())
                .severity("HIGH")
                .build();

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }
}
