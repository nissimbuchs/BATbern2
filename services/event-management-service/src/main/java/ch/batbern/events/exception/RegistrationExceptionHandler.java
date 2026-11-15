package ch.batbern.events.exception;

import ch.batbern.shared.dto.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/**
 * The registrations module's error responses that carry more than {@code details.code} (Story 17.14).
 * They are expected refusals, so they log at INFO. Every other domain exception is mapped once by
 * {@code GlobalExceptionHandler}.
 *
 * <p>{@link Ordered#HIGHEST_PRECEDENCE}: {@code GlobalExceptionHandler} maps every
 * {@link DomainException} and has a catch-all, so this advice must run first.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
@Slf4j
public class RegistrationExceptionHandler {

    /** Add participant to a full event without force (409, counts): the dialog offers "add anyway". */
    @ExceptionHandler(RegistrationCapacityExceededException.class)
    public ResponseEntity<ErrorResponse> handleCapacityExceeded(
            RegistrationCapacityExceededException ex,
            HttpServletRequest request) {
        log.info("Add-participant refused, event at capacity: {}", ex.getMessage());
        Map<String, Object> details = DomainErrorResponse.codeDetails(ex);
        details.put("activeCount", ex.getActiveCount());
        details.put("capacity", ex.getCapacity());
        return DomainErrorResponse.of(ex, request, ex.getMessage(), details);
    }

    /** Self-registration or waitlist join after the deadline (409, the deadline when known). */
    @ExceptionHandler(RegistrationClosedException.class)
    public ResponseEntity<ErrorResponse> handleRegistrationClosed(
            RegistrationClosedException ex,
            HttpServletRequest request) {
        log.info("Registration closed: {}", ex.getMessage());
        Map<String, Object> details = DomainErrorResponse.codeDetails(ex);
        if (ex.getDeadline() != null) {
            details.put("deadline", ex.getDeadline().toString());
        }
        return DomainErrorResponse.of(ex, request, ex.getMessage(), details);
    }

    /**
     * Authenticated registration with a profile lacking first or last name (409, which fields): the
     * frontend shows an inline profile form and retries. The message names no user.
     */
    @ExceptionHandler(RegistrationProfileIncompleteException.class)
    public ResponseEntity<ErrorResponse> handleProfileIncomplete(
            RegistrationProfileIncompleteException ex,
            HttpServletRequest request) {
        log.info("Refused authenticated registration, profile incomplete: {}", ex.getMessage());
        Map<String, Object> details = DomainErrorResponse.codeDetails(ex);
        details.put("username", ex.getUsername());
        details.put("missingFirstName", ex.isMissingFirstName());
        details.put("missingLastName", ex.isMissingLastName());
        return DomainErrorResponse.of(ex, request,
                "Profile incomplete — please add your first and last name before registering", details);
    }
}
