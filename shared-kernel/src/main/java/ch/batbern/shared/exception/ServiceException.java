package ch.batbern.shared.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;
import java.util.Map;

@ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
public class ServiceException extends BATbernException {
    public ServiceException(String message) {
        super(ErrorCode.ERR_SERVICE, message, Severity.CRITICAL);
    }

    public ServiceException(String message, Throwable cause) {
        super(ErrorCode.ERR_SERVICE, message, Severity.CRITICAL);
        initCause(cause);
    }

    public ServiceException(String message, Map<String, Object> details) {
        super(ErrorCode.ERR_SERVICE, message, details, Severity.CRITICAL);
    }
}
