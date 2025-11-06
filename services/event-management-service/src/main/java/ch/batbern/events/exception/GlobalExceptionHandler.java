package ch.batbern.events.exception;

import ch.batbern.shared.dto.ErrorResponse;
import ch.batbern.shared.exception.ValidationException;
import ch.batbern.shared.util.CorrelationIdGenerator;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mapping.PropertyReferenceException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
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
     * Handle generic exceptions
     * Returns HTTP 500 Internal Server Error
     */
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
