package ch.batbern.events.mailcenter;

import ch.batbern.events.mailcenter.compose.AlreadySentException;
import ch.batbern.events.mailcenter.compose.UnknownPlaceholderException;
import ch.batbern.shared.dto.ErrorResponse;
import ch.batbern.shared.util.CorrelationIdGenerator;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * The mail center's own error responses. They live in the module, not in
 * {@code GlobalExceptionHandler}, so the platform advice does not depend on the mail center.
 *
 * <p>{@code GlobalExceptionHandler} has no order and a catch-all {@code Exception} handler, so this
 * advice must run first: without {@link Ordered#HIGHEST_PRECEDENCE} the catch-all could win and
 * turn these into 500s.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
@Slf4j
public class MailCenterExceptionHandler {

    /** Own text names a placeholder the audience does not have (400). */
    @ExceptionHandler(UnknownPlaceholderException.class)
    public ResponseEntity<ErrorResponse> handleUnknownPlaceholderException(
            UnknownPlaceholderException ex,
            HttpServletRequest request) {
        log.warn("Own text with unknown placeholder(s): {}", ex.getPlaceholders());
        Map<String, Object> details = new HashMap<>();
        details.put("code", "UNKNOWN_PLACEHOLDER");
        details.put("placeholders", ex.getPlaceholders());
        ErrorResponse error = ErrorResponse.builder()
                .timestamp(Instant.now())
                .path(request.getRequestURI())
                .status(HttpStatus.BAD_REQUEST.value())
                .error("Bad Request")
                .message(ex.getMessage())
                .correlationId(CorrelationIdGenerator.current())
                .severity("LOW")
                .details(details)
                .build();
        return ResponseEntity.badRequest().body(error);
    }

    /** Own text with this subject already went to the registrants (409). */
    @ExceptionHandler(AlreadySentException.class)
    public ResponseEntity<ErrorResponse> handleAlreadySentException(
            AlreadySentException ex,
            HttpServletRequest request) {
        log.warn("Own-text registrant send needs confirmResend: {}", ex.getMessage());
        Map<String, Object> details = new HashMap<>();
        details.put("code", "ALREADY_SENT");
        details.put("sentAt", ex.getSentAt().toString());
        ErrorResponse error = ErrorResponse.builder()
                .timestamp(Instant.now())
                .path(request.getRequestURI())
                .status(HttpStatus.CONFLICT.value())
                .error("Conflict")
                .message(ex.getMessage())
                .correlationId(CorrelationIdGenerator.current())
                .severity("LOW")
                .details(details)
                .build();
        return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
    }
}
