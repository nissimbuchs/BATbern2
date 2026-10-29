package ch.batbern.shared.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.ErrorResponse;

import java.sql.SQLException;
import java.time.Instant;
import java.util.Optional;

/**
 * Decides whether an exception that reached a catch-all {@code @ExceptionHandler(Exception.class)}
 * is really the client's fault.
 *
 * <p>Every service's catch-all turned client mistakes into 500 CRITICAL (issues #1148, #1157,
 * #1103): a missing query parameter or header, a form-urlencoded body, an unknown path, a NUL
 * byte. Mapping each Spring exception per service left gaps whenever one handler was missed, so
 * the catch-alls ask this class first instead.
 *
 * <ul>
 *   <li>Spring MVC exceptions implementing {@link ErrorResponse} carry their own status. A 4xx
 *       there (missing parameter/header, 404 no resource, 405, 406, 415) is kept.</li>
 *   <li>SQLState {@code 22021} ({@code character_not_in_repertoire}): PostgreSQL rejects a NUL
 *       byte in text input. Only a client can put one there.</li>
 * </ul>
 */
public final class ClientErrorClassifier {

    /** PostgreSQL: invalid byte sequence for the encoding, i.e. {@code 0x00} in text input. */
    static final String SQLSTATE_INVALID_CHARACTER = "22021";

    private static final int MAX_CAUSE_DEPTH = 10;

    private ClientErrorClassifier() {
    }

    public record ClientError(HttpStatusCode status, String message) {

        /** The standard error body for this client error, severity LOW: it is not our fault. */
        public ResponseEntity<ch.batbern.shared.dto.ErrorResponse> toResponseEntity(
                String path, String correlationId) {
            HttpStatus resolved = HttpStatus.resolve(status.value());
            ch.batbern.shared.dto.ErrorResponse body = ch.batbern.shared.dto.ErrorResponse.builder()
                    .timestamp(Instant.now())
                    .path(path)
                    .status(status.value())
                    .error(resolved != null ? resolved.getReasonPhrase() : "Client Error")
                    .message(message)
                    .correlationId(correlationId)
                    .severity("LOW")
                    .build();
            return ResponseEntity.status(status).body(body);
        }
    }

    public static Optional<ClientError> classify(Throwable ex) {
        if (ex instanceof ErrorResponse errorResponse
                && errorResponse.getStatusCode().is4xxClientError()) {
            return Optional.of(new ClientError(errorResponse.getStatusCode(), detailOf(errorResponse)));
        }
        if (hasSqlState(ex, SQLSTATE_INVALID_CHARACTER)) {
            return Optional.of(new ClientError(HttpStatus.BAD_REQUEST, "Request contains invalid characters"));
        }
        return Optional.empty();
    }

    private static String detailOf(ErrorResponse errorResponse) {
        ProblemDetail body = errorResponse.getBody();
        if (body.getDetail() != null) {
            return body.getDetail();
        }
        HttpStatus status = HttpStatus.resolve(errorResponse.getStatusCode().value());
        return status != null ? status.getReasonPhrase() : "Bad request";
    }

    private static boolean hasSqlState(Throwable ex, String sqlState) {
        Throwable current = ex;
        for (int depth = 0; current != null && depth < MAX_CAUSE_DEPTH; depth++) {
            if (current instanceof SQLException sql && sqlState.equals(sql.getSQLState())) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
