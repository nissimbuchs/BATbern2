package ch.batbern.shared.utils;

import java.util.Collections;
import java.util.Map;

public class LogContext {
    private final Map<String, Object> fields;

    public LogContext(Map<String, Object> fields) {
        this.fields = Collections.unmodifiableMap(fields);
    }

    public Map<String, Object> getFields() {
        return fields;
    }

    @Override
    public String toString() {
        return LoggingUtils.formatStructuredMessage("", fields);
    }
}