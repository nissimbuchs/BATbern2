package ch.batbern.events.exception;

import ch.batbern.shared.dto.ErrorResponse;
import ch.batbern.shared.util.CorrelationIdGenerator;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * The one builder of the error body of a {@link DomainException} (Story 17.14), used by
 * {@code GlobalExceptionHandler} and by the module advices that add fields to {@code details}.
 *
 * <p>The body is the shared {@link ErrorResponse} ({@code NON_NULL}): {@code details} is left out when
 * it is {@code null} and written as {@code {}} when it is empty. The top-level {@code errorCode} stays
 * unset; the code goes into {@code details.code}, where clients read it.
 */
public final class DomainErrorResponse {

    private DomainErrorResponse() {
    }

    /** The standard body: the exception's message, {@code details.code} when it has a code. */
    public static ResponseEntity<ErrorResponse> of(DomainException ex, HttpServletRequest request) {
        return of(ex, request, ex.getMessage(), ex.getCode() == null ? null : codeDetails(ex));
    }

    /** A body with the given message and details ({@code null}: no {@code details} field). */
    public static ResponseEntity<ErrorResponse> of(DomainException ex, HttpServletRequest request,
            String message, Map<String, Object> details) {
        ErrorResponse error = ErrorResponse.builder()
                .timestamp(Instant.now())
                .path(request.getRequestURI())
                .status(ex.getStatus().value())
                .error(ex.getStatus().getReasonPhrase())
                .message(message)
                .correlationId(CorrelationIdGenerator.current())
                .severity(ex.getSeverity().name())
                .details(details)
                .build();
        return ResponseEntity.status(ex.getStatus()).body(error);
    }

    /** A mutable details map holding {@code code} when the exception has one, for an advice to extend. */
    public static Map<String, Object> codeDetails(DomainException ex) {
        Map<String, Object> details = new HashMap<>();
        if (ex.getCode() != null) {
            details.put("code", ex.getCode());
        }
        return details;
    }
}
