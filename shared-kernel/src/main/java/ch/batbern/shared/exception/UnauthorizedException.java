package ch.batbern.shared.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.UNAUTHORIZED)
public class UnauthorizedException extends BATbernException {
    public UnauthorizedException(String message) {
        super(ErrorCode.ERR_UNAUTHORIZED, message, Severity.HIGH);
    }

    public UnauthorizedException() {
        super(
            ErrorCode.ERR_UNAUTHORIZED,
            "Authentication required to access this resource",
            Severity.HIGH
        );
    }
}
