package ch.batbern.companyuser.exception;

import ch.batbern.shared.dto.ErrorResponse;
import ch.batbern.shared.exception.ValidationException;
import ch.batbern.shared.util.CorrelationIdGenerator;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Global exception handler for REST API
 * AC4: Proper error handling and responses
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(CompanyNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleCompanyNotFoundException(
            CompanyNotFoundException ex,
            HttpServletRequest request) {
        log.error("Company not found: {}", ex.getMessage());
        ErrorResponse error = ErrorResponse.builder()
                .timestamp(Instant.now())
                .path(request.getRequestURI())
                .status(HttpStatus.NOT_FOUND.value())
                .error("Not Found")
                .message(ex.getMessage())
                .correlationId(CorrelationIdGenerator.generate())
                .severity("WARNING")
                .build();
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }

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

    @ExceptionHandler(UserValidationException.class)
    public ResponseEntity<ErrorResponse> handleUserValidationException(
            UserValidationException ex,
            HttpServletRequest request) {
        log.warn("User validation error: {}", ex.getMessage());
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

    @ExceptionHandler(MinimumOrganizersException.class)
    public ResponseEntity<ErrorResponse> handleMinimumOrganizersException(
            MinimumOrganizersException ex,
            HttpServletRequest request) {
        log.warn("Business rule violation: {}", ex.getMessage());
        ErrorResponse error = ErrorResponse.builder()
                .timestamp(Instant.now())
                .path(request.getRequestURI())
                .status(HttpStatus.UNPROCESSABLE_ENTITY.value())
                .error("Business Rule Violation")
                .message(ex.getMessage())
                .correlationId(CorrelationIdGenerator.generate())
                .severity("HIGH")
                .details(ex.getDetails())
                .build();
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(error);
    }

    @ExceptionHandler(InvalidUIDException.class)
    public ResponseEntity<ErrorResponse> handleInvalidUIDException(
            InvalidUIDException ex,
            HttpServletRequest request) {
        log.error("Invalid Swiss UID: {}", ex.getMessage());
        ErrorResponse error = ErrorResponse.builder()
                .timestamp(Instant.now())
                .path(request.getRequestURI())
                .status(HttpStatus.BAD_REQUEST.value())
                .error("Bad Request")
                .message(ex.getMessage())
                .correlationId(CorrelationIdGenerator.generate())
                .severity("WARNING")
                .build();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    @ExceptionHandler(CompanyValidationException.class)
    public ResponseEntity<ErrorResponse> handleCompanyValidationException(
            CompanyValidationException ex,
            HttpServletRequest request) {
        log.error("Company validation failed: {}", ex.getMessage());
        ErrorResponse error = ErrorResponse.builder()
                .timestamp(Instant.now())
                .path(request.getRequestURI())
                .status(HttpStatus.CONFLICT.value())
                .error("Conflict")
                .message(ex.getMessage())
                .correlationId(CorrelationIdGenerator.generate())
                .severity("WARNING")
                .build();
        return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationExceptions(
            MethodArgumentNotValidException ex,
            HttpServletRequest request) {
        log.error("Validation error: {}", ex.getMessage());

        Map<String, Object> validationErrors = new HashMap<>();
        StringBuilder messageBuilder = new StringBuilder("Validation failed: ");
        boolean first = true;

        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            String fieldName = error.getField();
            String errorMessage = error.getDefaultMessage();
            validationErrors.put(fieldName, errorMessage);

            if (!first) {
                messageBuilder.append(", ");
            }
            messageBuilder.append(fieldName).append(" - ").append(errorMessage);
            first = false;
        }

        ErrorResponse error = ErrorResponse.builder()
                .timestamp(Instant.now())
                .path(request.getRequestURI())
                .status(HttpStatus.BAD_REQUEST.value())
                .error("Validation Failed")
                .message(messageBuilder.toString())
                .correlationId(CorrelationIdGenerator.generate())
                .severity("WARNING")
                .details(validationErrors)
                .build();

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    /**
     * Handle missing request parameters
     * Returns 400 Bad Request when required request parameters are missing
     */
    @ExceptionHandler(org.springframework.web.bind.MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingServletRequestParameter(
            org.springframework.web.bind.MissingServletRequestParameterException ex,
            HttpServletRequest request) {
        log.error("Missing required parameter: {}", ex.getMessage());
        ErrorResponse error = ErrorResponse.builder()
                .timestamp(Instant.now())
                .path(request.getRequestURI())
                .status(HttpStatus.BAD_REQUEST.value())
                .error("Bad Request")
                .message(String.format("Required parameter '%s' is missing", ex.getParameterName()))
                .correlationId(CorrelationIdGenerator.generate())
                .severity("WARNING")
                .build();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    /**
     * Handle shared-kernel ValidationException (filter parsing, pagination params, etc.)
     * AC14: Return 400 Bad Request for invalid query parameters
     */
    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<ErrorResponse> handleSharedValidationException(
            ValidationException ex,
            HttpServletRequest request) {
        log.error("Query parameter validation error: {}", ex.getMessage());
        ErrorResponse error = ErrorResponse.builder()
                .timestamp(Instant.now())
                .path(request.getRequestURI())
                .status(HttpStatus.BAD_REQUEST.value())
                .error("Bad Request")
                .message(ex.getMessage())
                .correlationId(CorrelationIdGenerator.generate())
                .severity("WARNING")
                .build();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    /**
     * Handle Spring Security AuthorizationDeniedException
     * AC10: Return 401 for anonymous users, 403 for authenticated users without permission
     */
    @ExceptionHandler(AuthorizationDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAuthorizationDeniedException(
            AuthorizationDeniedException ex,
            HttpServletRequest request) {
        log.error("Authorization denied: {}", ex.getMessage());

        // Check if the user is anonymous (not authenticated)
        org.springframework.security.core.Authentication auth =
            org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();

        boolean isAnonymous = auth == null
                || auth instanceof org.springframework.security.authentication.AnonymousAuthenticationToken
                || !auth.isAuthenticated();

        if (isAnonymous) {
            // Return 401 Unauthorized for anonymous users
            ErrorResponse error = ErrorResponse.builder()
                    .timestamp(Instant.now())
                    .path(request.getRequestURI())
                    .status(HttpStatus.UNAUTHORIZED.value())
                    .error("Unauthorized")
                    .message("Authentication required")
                    .correlationId(CorrelationIdGenerator.generate())
                    .severity("WARNING")
                    .build();
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
        } else {
            // Return 403 Forbidden for authenticated users without sufficient permissions
            ErrorResponse error = ErrorResponse.builder()
                    .timestamp(Instant.now())
                    .path(request.getRequestURI())
                    .status(HttpStatus.FORBIDDEN.value())
                    .error("Forbidden")
                    .message("Access denied")
                    .correlationId(CorrelationIdGenerator.generate())
                    .severity("WARNING")
                    .build();
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error);
        }
    }

    /**
     * Handle Spring Security AccessDeniedException (legacy)
     * AC10: Return 403 Forbidden for insufficient permissions
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDeniedException(
            AccessDeniedException ex,
            HttpServletRequest request) {
        log.error("Access denied: {}", ex.getMessage());
        ErrorResponse error = ErrorResponse.builder()
                .timestamp(Instant.now())
                .path(request.getRequestURI())
                .status(HttpStatus.FORBIDDEN.value())
                .error("Forbidden")
                .message("Access denied")
                .correlationId(CorrelationIdGenerator.generate())
                .severity("WARNING")
                .build();
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error);
    }

    /**
     * AC10: Handle file size exceeded exception
     */
    @ExceptionHandler(FileSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleFileSizeExceededException(
            FileSizeExceededException ex,
            HttpServletRequest request) {
        log.warn("File size exceeded: {}", ex.getMessage());
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
     * AC10: Handle invalid file type exception
     */
    @ExceptionHandler(InvalidFileTypeException.class)
    public ResponseEntity<ErrorResponse> handleInvalidFileTypeException(
            InvalidFileTypeException ex,
            HttpServletRequest request) {
        log.warn("Invalid file type: {}", ex.getMessage());
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
     * Story 1.16.3: Handle logo not found exception
     */
    @ExceptionHandler(LogoNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleLogoNotFoundException(
            LogoNotFoundException ex,
            HttpServletRequest request) {
        log.warn("Logo not found: {}", ex.getMessage());
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
     * Story 1.16.3: Handle illegal state exceptions (e.g., deleting associated logos)
     * Returns 400 Bad Request for business rule violations
     */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorResponse> handleIllegalStateException(
            IllegalStateException ex,
            HttpServletRequest request) {
        log.warn("Illegal state: {}", ex.getMessage());
        ErrorResponse error = ErrorResponse.builder()
                .timestamp(Instant.now())
                .path(request.getRequestURI())
                .status(HttpStatus.BAD_REQUEST.value())
                .error("Bad Request")
                .message(ex.getMessage())
                .correlationId(CorrelationIdGenerator.generate())
                .severity("WARNING")
                .build();
        return ResponseEntity.badRequest().body(error);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(
            Exception ex,
            HttpServletRequest request) {
        log.error("Unexpected error: ", ex);
        ErrorResponse error = ErrorResponse.builder()
                .timestamp(Instant.now())
                .path(request.getRequestURI())
                .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                .error("Internal Server Error")
                .message("An unexpected error occurred")
                .correlationId(CorrelationIdGenerator.generate())
                .severity("ERROR")
                .build();
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }
}
