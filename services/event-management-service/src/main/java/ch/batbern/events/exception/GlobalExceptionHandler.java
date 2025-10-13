package ch.batbern.events.exception;

import ch.batbern.shared.exception.ValidationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mapping.PropertyReferenceException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

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
    public ResponseEntity<Map<String, Object>> handleValidationException(ValidationException ex) {
        log.warn("Validation error: {}", ex.getMessage());

        return ResponseEntity
                .badRequest()
                .body(Map.of(
                        "error", "VALIDATION_ERROR",
                        "message", ex.getMessage()
                ));
    }

    /**
     * Handle PropertyReferenceException (invalid field name in sort)
     * Returns HTTP 400 Bad Request
     */
    @ExceptionHandler(PropertyReferenceException.class)
    public ResponseEntity<Map<String, Object>> handlePropertyReferenceException(PropertyReferenceException ex) {
        log.warn("Invalid property reference: {}", ex.getMessage());

        return ResponseEntity
                .badRequest()
                .body(Map.of(
                        "error", "VALIDATION_ERROR",
                        "message", "Invalid field name in sort or filter: " + ex.getPropertyName()
                ));
    }

    /**
     * Handle MethodArgumentNotValidException (Jakarta validation errors)
     * Returns HTTP 400 Bad Request
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleMethodArgumentNotValidException(MethodArgumentNotValidException ex) {
        String errors = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining(", "));

        log.warn("Validation error: {}", errors);

        return ResponseEntity
                .badRequest()
                .body(Map.of(
                        "error", "VALIDATION_ERROR",
                        "message", "Validation failed: " + errors
                ));
    }

    /**
     * Handle EventNotFoundException (event not found by ID)
     * Returns HTTP 404 Not Found
     */
    @ExceptionHandler(EventNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleEventNotFoundException(EventNotFoundException ex) {
        log.warn("Event not found: {}", ex.getMessage());

        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(Map.of(
                        "error", "NOT_FOUND",
                        "message", ex.getMessage()
                ));
    }

    /**
     * Handle BusinessValidationException (business logic validation failures)
     * Returns HTTP 422 Unprocessable Entity with VALIDATION_ERROR code
     */
    @ExceptionHandler(BusinessValidationException.class)
    public ResponseEntity<Map<String, Object>> handleBusinessValidationException(BusinessValidationException ex) {
        log.warn("Business validation error: {}", ex.getMessage());

        return ResponseEntity
                .status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(Map.of(
                        "error", "VALIDATION_ERROR",
                        "message", ex.getMessage()
                ));
    }

    /**
     * Handle WorkflowException (invalid workflow state transition)
     * Returns HTTP 422 Unprocessable Entity with WORKFLOW_ERROR code
     */
    @ExceptionHandler(WorkflowException.class)
    public ResponseEntity<Map<String, Object>> handleWorkflowException(WorkflowException ex) {
        log.warn("Workflow error: {}", ex.getMessage());

        return ResponseEntity
                .status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(Map.of(
                        "error", "WORKFLOW_ERROR",
                        "message", ex.getMessage()
                ));
    }

    /**
     * Handle generic exceptions
     * Returns HTTP 500 Internal Server Error
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGenericException(Exception ex) {
        log.error("Unexpected error", ex);

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of(
                        "error", "INTERNAL_ERROR",
                        "message", "An unexpected error occurred"
                ));
    }
}
