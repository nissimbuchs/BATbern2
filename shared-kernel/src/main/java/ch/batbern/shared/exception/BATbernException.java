package ch.batbern.shared.exception;

import lombok.Getter;
import java.util.HashMap;
import java.util.Map;

@Getter
public abstract class BATbernException extends RuntimeException {
    private final ErrorCode errorCode;
    private final Map<String, Object> details;
    private final Severity severity;

    protected BATbernException(
            ErrorCode errorCode,
            String message,
            Map<String, Object> details,
            Severity severity) {
        super(message);
        this.errorCode = errorCode;
        this.details = details != null ? details : new HashMap<>();
        this.severity = severity;
    }

    protected BATbernException(
            ErrorCode errorCode,
            String message,
            Severity severity) {
        this(errorCode, message, null, severity);
    }

    public enum Severity {
        LOW, MEDIUM, HIGH, CRITICAL
    }
}
