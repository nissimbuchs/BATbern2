package ch.batbern.agent.exception;

import ch.batbern.shared.dto.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.Instant;

/** Maps a bad request parameter to the shared {@link ErrorResponse} (400). */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler({HandlerMethodValidationException.class, ConstraintViolationException.class,
        MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ErrorResponse> badParameter(Exception e, HttpServletRequest request) {
        HttpStatus status = HttpStatus.BAD_REQUEST;
        return ResponseEntity.status(status).body(ErrorResponse.builder()
                .timestamp(Instant.now())
                .path(request.getRequestURI())
                .status(status.value())
                .error(status.getReasonPhrase())
                .errorCode("ERR_VALIDATION")
                .message("Invalid request parameter")
                .severity("LOW")
                .build());
    }
}
