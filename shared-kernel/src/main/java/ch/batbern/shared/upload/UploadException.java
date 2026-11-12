package ch.batbern.shared.upload;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * A refused upload step. A {@link ResponseStatusException}, so every service's exception handler
 * answers it with its status and the standard error body.
 */
public class UploadException extends ResponseStatusException {

    public UploadException(HttpStatus status, String reason) {
        super(status, reason);
    }

    public static UploadException badRequest(String reason) {
        return new UploadException(HttpStatus.BAD_REQUEST, reason);
    }

    public static UploadException forbidden(String reason) {
        return new UploadException(HttpStatus.FORBIDDEN, reason);
    }

    public static UploadException notFound(String reason) {
        return new UploadException(HttpStatus.NOT_FOUND, reason);
    }

    public static UploadException conflict(String reason) {
        return new UploadException(HttpStatus.CONFLICT, reason);
    }

    public static UploadException badGateway(String reason) {
        return new UploadException(HttpStatus.BAD_GATEWAY, reason);
    }
}
