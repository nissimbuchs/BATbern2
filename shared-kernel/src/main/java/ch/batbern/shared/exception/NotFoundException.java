package ch.batbern.shared.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;
import java.util.Map;

@ResponseStatus(HttpStatus.NOT_FOUND)
public class NotFoundException extends BATbernException {
    public NotFoundException(String resourceType, String resourceId) {
        super(
            ErrorCode.ERR_NOT_FOUND,
            String.format("%s with ID '%s' not found", resourceType, resourceId),
            Map.of("resourceType", resourceType, "resourceId", resourceId),
            Severity.LOW
        );
    }

    public NotFoundException(String message) {
        super(ErrorCode.ERR_NOT_FOUND, message, Severity.LOW);
    }
}
