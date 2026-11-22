package ch.batbern.agent.exception;

import ch.batbern.shared.dto.ErrorResponse;
import ch.batbern.shared.exception.NotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.Clock;

/** Maps a bad request parameter (400) and an unknown run or call (404) to the shared {@link ErrorResponse}. */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** The decision log's clock (ClockConfig), so every timestamp agent-service writes has one source. */
    private final Clock clock;

    public GlobalExceptionHandler(Clock clock) {
        this.clock = clock;
    }

    @ExceptionHandler({HandlerMethodValidationException.class, ConstraintViolationException.class,
        MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ErrorResponse> badParameter(Exception e, HttpServletRequest request) {
        HttpStatus status = HttpStatus.BAD_REQUEST;
        return ResponseEntity.status(status).body(ErrorResponse.builder()
                .timestamp(clock.instant())
                .path(request.getRequestURI())
                .status(status.value())
                .error(status.getReasonPhrase())
                .errorCode("ERR_VALIDATION")
                .message("Invalid request parameter")
                .severity("LOW")
                .build());
    }

    /** An unknown run, or a model call not of the run: 404 {@code ERR_NOT_FOUND}. */
    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ErrorResponse> notFound(NotFoundException e, HttpServletRequest request) {
        HttpStatus status = HttpStatus.NOT_FOUND;
        return ResponseEntity.status(status).body(ErrorResponse.builder()
                .timestamp(clock.instant())
                .path(request.getRequestURI())
                .status(status.value())
                .error(status.getReasonPhrase())
                .errorCode(e.getErrorCode().name())
                .message(e.getMessage())
                .severity("LOW")
                .build());
    }
}
