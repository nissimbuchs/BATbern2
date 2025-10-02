package ch.batbern.gateway.exception;

import ch.batbern.shared.dto.ErrorResponse;
import ch.batbern.shared.exception.BATbernException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@ControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @Value("${spring.profiles.active:dev}")
    private String activeProfile;

    private static final String CORRELATION_ID = "correlationId";

    @ExceptionHandler(BATbernException.class)
    public ResponseEntity<ErrorResponse> handleBATbernException(
            BATbernException ex,
            HttpServletRequest request) {

        String correlationId = MDC.get(CORRELATION_ID);

        // Log based on severity
        logException(ex, correlationId);

        ErrorResponse error = ErrorResponse.builder()
                .timestamp(Instant.now())
                .path(request.getRequestURI())
                .status(getHttpStatus(ex).value())
                .error(ex.getErrorCode().name())
                .message(ex.getMessage())
                .correlationId(correlationId)
                .severity(ex.getSeverity().name())
                .details(sanitizeDetails(ex.getDetails()))
                .stackTrace(includeStackTrace() ? getStackTrace(ex) : null)
                .build();

        return ResponseEntity.status(getHttpStatus(ex)).body(error);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(
            MethodArgumentNotValidException ex,
            HttpServletRequest request) {

        String correlationId = MDC.get(CORRELATION_ID);

        Map<String, Object> validationErrors = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error ->
            validationErrors.put(error.getField(), error.getDefaultMessage())
        );

        log.warn("Validation error [{}]: {}", correlationId, validationErrors);

        ErrorResponse error = ErrorResponse.builder()
                .timestamp(Instant.now())
                .path(request.getRequestURI())
                .status(HttpStatus.BAD_REQUEST.value())
                .error("ERR_VALIDATION")
                .message("Request validation failed")
                .correlationId(correlationId)
                .severity("MEDIUM")
                .details(Map.of("fieldErrors", validationErrors))
                .build();

        return ResponseEntity.badRequest().body(error);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(
            Exception ex,
            HttpServletRequest request) {

        String correlationId = MDC.get(CORRELATION_ID);

        log.error("Unexpected error [{}]: ", correlationId, ex);

        ErrorResponse error = ErrorResponse.builder()
                .timestamp(Instant.now())
                .path(request.getRequestURI())
                .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                .error("ERR_SERVICE")
                .message("An unexpected error occurred. Please try again later.")
                .correlationId(correlationId)
                .severity("CRITICAL")
                .stackTrace(includeStackTrace() ? getStackTrace(ex) : null)
                .build();

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }

    private void logException(BATbernException ex, String correlationId) {
        switch (ex.getSeverity()) {
            case LOW:
                log.info("Domain exception [{}] - {}: {}",
                    correlationId, ex.getErrorCode(), ex.getMessage());
                break;
            case MEDIUM:
                log.warn("Domain exception [{}] - {}: {}",
                    correlationId, ex.getErrorCode(), ex.getMessage());
                break;
            case HIGH:
            case CRITICAL:
                log.error("Domain exception [{}] - {}: {}",
                    correlationId, ex.getErrorCode(), ex.getMessage(), ex);
                break;
        }
    }

    private HttpStatus getHttpStatus(BATbernException ex) {
        // Extract from @ResponseStatus annotation if present
        ResponseStatus annotation = ex.getClass().getAnnotation(ResponseStatus.class);
        return annotation != null ? annotation.value() : HttpStatus.INTERNAL_SERVER_ERROR;
    }

    private Map<String, Object> sanitizeDetails(Map<String, Object> details) {
        if (details == null) return null;

        Map<String, Object> sanitized = new HashMap<>(details);
        // Remove sensitive fields
        sanitized.remove("password");
        sanitized.remove("token");
        sanitized.remove("secret");
        sanitized.remove("apiKey");
        return sanitized;
    }

    private boolean includeStackTrace() {
        return "dev".equals(activeProfile) || "staging".equals(activeProfile);
    }

    private String getStackTrace(Exception ex) {
        StringBuilder sb = new StringBuilder();
        for (StackTraceElement element : ex.getStackTrace()) {
            sb.append(element.toString()).append("\n");
        }
        return sb.toString();
    }
}
