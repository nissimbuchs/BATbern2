package ch.batbern.events.exception;

import ch.batbern.shared.dto.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * The event core module's error response that carries more than {@code details.code} (Story 17.14).
 * Every other domain exception is mapped once by {@code GlobalExceptionHandler}.
 *
 * <p>{@link Ordered#HIGHEST_PRECEDENCE}: {@code GlobalExceptionHandler} maps every
 * {@link DomainException} and has a catch-all, so this advice must run first.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
@Slf4j
public class EventWorkflowExceptionHandler {

    /**
     * Story 5.1a AC12: a business rule blocks the workflow transition (422). {@code details} is the
     * rule's context, written as {@code {}} when it has none.
     */
    @ExceptionHandler(WorkflowValidationException.class)
    public ResponseEntity<ErrorResponse> handleWorkflowValidation(
            WorkflowValidationException ex,
            HttpServletRequest request) {
        log.warn("Workflow validation error: {}", ex.getMessage());
        return DomainErrorResponse.of(ex, request, ex.getMessage(), ex.getContext());
    }
}
