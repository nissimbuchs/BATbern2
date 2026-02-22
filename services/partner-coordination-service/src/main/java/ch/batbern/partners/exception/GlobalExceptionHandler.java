package ch.batbern.partners.exception;

import ch.batbern.shared.dto.ErrorResponse;
import ch.batbern.shared.exception.NotFoundException;
import ch.batbern.shared.exception.ValidationException;
import ch.batbern.shared.util.CorrelationIdGenerator;
import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;

/**
 * Global exception handler using shared-kernel ErrorResponse.
 *
 * Handles all exceptions and converts them to standardized ErrorResponse format.
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /**
     * Handle PartnerNotFoundException (partner not found by company name)
     * Returns HTTP 404 Not Found
     */
    @ExceptionHandler(PartnerNotFoundException.class)
    public ResponseEntity<ErrorResponse> handlePartnerNotFoundException(
            PartnerNotFoundException ex,
            HttpServletRequest request) {
        log.warn("Partner not found: {}", ex.getMessage());

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
     * Handle CompanyNotFoundException (company not found via Company Service API)
     * Returns HTTP 404 Not Found
     */
    @ExceptionHandler(CompanyNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleCompanyNotFoundException(
            CompanyNotFoundException ex,
            HttpServletRequest request) {
        log.warn("Company not found: {}", ex.getMessage());

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
     * Handle PartnerAlreadyExistsException (duplicate partner creation attempt)
     * Returns HTTP 400 Bad Request
     */
    @ExceptionHandler(PartnerAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> handlePartnerAlreadyExistsException(
            PartnerAlreadyExistsException ex,
            HttpServletRequest request) {
        log.warn("Partner already exists: {}", ex.getMessage());

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
     * Handle UserNotFoundException (user not found via User Service API)
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
     * Handle NotFoundException (generic not found from shared-kernel)
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
                .severity("LOW")
                .build();

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }

    /**
     * Handle EntityNotFoundException (JPA entity not found) — Story 8.2.
     * Returns HTTP 404 Not Found
     */
    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleEntityNotFoundException(
            EntityNotFoundException ex,
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
     * Handle ValidationException (business rule violations from shared-kernel)
     * Returns HTTP 409 Conflict for duplicates, HTTP 400 Bad Request for other violations
     */
    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(
            ValidationException ex,
            HttpServletRequest request) {
        log.warn("Validation error: {}", ex.getMessage());

        // Determine if this is a duplicate (409) or other validation error (400)
        boolean isDuplicate = ex.getMessage().contains("already exists");
        HttpStatus status = isDuplicate ? HttpStatus.CONFLICT : HttpStatus.BAD_REQUEST;

        ErrorResponse error = ErrorResponse.builder()
                .timestamp(Instant.now())
                .path(request.getRequestURI())
                .status(status.value())
                .error(isDuplicate ? "Conflict" : "Bad Request")
                .message(ex.getMessage())
                .correlationId(CorrelationIdGenerator.generate())
                .severity("MEDIUM")
                .build();

        return ResponseEntity.status(status).body(error);
    }

    /**
     * Handle IllegalArgumentException (validation errors in service/domain layer)
     * Returns HTTP 400 Bad Request
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgumentException(
            IllegalArgumentException ex,
            HttpServletRequest request) {
        log.warn("Illegal argument: {}", ex.getMessage());

        ErrorResponse error = ErrorResponse.builder()
                .timestamp(Instant.now())
                .path(request.getRequestURI())
                .status(HttpStatus.BAD_REQUEST.value())
                .error("Bad Request")
                .message(ex.getMessage())
                .correlationId(CorrelationIdGenerator.generate())
                .severity("LOW")
                .build();

        return ResponseEntity.badRequest().body(error);
    }

    /**
     * Handle ConstraintViolationException (bean validation errors)
     * Returns HTTP 400 Bad Request
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolationException(
            ConstraintViolationException ex,
            HttpServletRequest request) {
        log.warn("Validation constraint violated: {}", ex.getMessage());

        // Extract validation messages from constraint violations
        String validationMessages = ex.getConstraintViolations().stream()
                .map(violation -> violation.getPropertyPath() + ": " + violation.getMessage())
                .reduce((a, b) -> a + "; " + b)
                .orElse("Validation failed");

        ErrorResponse error = ErrorResponse.builder()
                .timestamp(Instant.now())
                .path(request.getRequestURI())
                .status(HttpStatus.BAD_REQUEST.value())
                .error("Bad Request")
                .message("Validation failed: " + validationMessages)
                .correlationId(CorrelationIdGenerator.generate())
                .severity("MEDIUM")
                .build();

        return ResponseEntity.badRequest().body(error);
    }

    /**
     * Handle Spring Security access denied (insufficient role / @PreAuthorize failed).
     * Story 8.1: AC6 — returns 403 when PARTNER tries to access another company's analytics.
     * Returns HTTP 403 Forbidden
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDeniedException(
            AccessDeniedException ex,
            HttpServletRequest request) {
        log.warn("Access denied: {}", request.getRequestURI());

        ErrorResponse error = ErrorResponse.builder()
                .timestamp(java.time.Instant.now())
                .path(request.getRequestURI())
                .status(HttpStatus.FORBIDDEN.value())
                .error("Forbidden")
                .message("Access denied")
                .correlationId(CorrelationIdGenerator.generate())
                .severity("LOW")
                .build();

        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error);
    }

    /**
     * Handle Spring Security authentication exceptions (not authenticated).
     * Returns HTTP 401 Unauthorized
     */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuthenticationException(
            AuthenticationException ex,
            HttpServletRequest request) {
        log.warn("Authentication required: {}", request.getRequestURI());

        ErrorResponse error = ErrorResponse.builder()
                .timestamp(java.time.Instant.now())
                .path(request.getRequestURI())
                .status(HttpStatus.UNAUTHORIZED.value())
                .error("Unauthorized")
                .message("Authentication required")
                .correlationId(CorrelationIdGenerator.generate())
                .severity("LOW")
                .build();

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
    }

    /**
     * Handle @Valid bean validation failures (e.g. @NotBlank, @NotNull on request bodies).
     * Returns HTTP 400 Bad Request.
     *
     * Story 8.3: required to return 400 for invalid CreateMeetingRequest.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(
            MethodArgumentNotValidException ex,
            HttpServletRequest request) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .reduce((a, b) -> a + "; " + b)
                .orElse("Validation failed");

        ErrorResponse error = ErrorResponse.builder()
                .timestamp(Instant.now())
                .path(request.getRequestURI())
                .status(HttpStatus.BAD_REQUEST.value())
                .error("Bad Request")
                .message(message)
                .correlationId(CorrelationIdGenerator.generate())
                .severity("LOW")
                .build();

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
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
