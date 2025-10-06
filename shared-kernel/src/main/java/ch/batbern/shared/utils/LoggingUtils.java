package ch.batbern.shared.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.MDC;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class LoggingUtils {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    private LoggingUtils() {
        // Utility class, prevent instantiation
    }

    public static String formatStructuredMessage(String message, Map<String, Object> context) {
        StringBuilder sb = new StringBuilder(message);
        if (!context.isEmpty()) {
            sb.append(" | ");
            String contextString = context.entrySet().stream()
                .map(e -> {
                    String value = e.getValue() == null ? "null" : "\"" + escapeValue(e.getValue().toString()) + "\"";
                    return e.getKey() + "=" + value;
                })
                .collect(Collectors.joining(" "));
            sb.append(contextString);
        }
        return sb.toString();
    }

    private static String escapeValue(String value) {
        return value.replace("\"", "\\\"")
                    .replace("\n", "\\n")
                    .replace("\r", "\\r")
                    .replace("\t", "\\t");
    }

    public static String formatAsJson(String message, Map<String, Object> context) {
        Map<String, Object> logEntry = new HashMap<>();
        logEntry.put("message", message);
        logEntry.put("timestamp", Instant.now().toString());
        logEntry.putAll(context);

        try {
            return objectMapper.writeValueAsString(logEntry);
        } catch (JsonProcessingException e) {
            return formatStructuredMessage(message, context);
        }
    }

    public static void setTraceContext(String traceId, String correlationId) {
        if (traceId != null) {
            MDC.put("traceId", traceId);
        }
        if (correlationId != null) {
            MDC.put("correlationId", correlationId);
        }
    }

    public static void clearContext() {
        MDC.remove("traceId");
        MDC.remove("correlationId");
    }

    public static <T> T withContext(Map<String, String> context, Callable<T> action) {
        // Store original MDC values
        Map<String, String> originalContext = new HashMap<>();
        for (String key : context.keySet()) {
            String original = MDC.get(key);
            if (original != null) {
                originalContext.put(key, original);
            }
        }

        try {
            // Set new context
            context.forEach(MDC::put);
            return action.call();
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            // Restore original context
            context.keySet().forEach(MDC::remove);
            originalContext.forEach(MDC::put);
        }
    }

    public static LogContextBuilder context() {
        return new LogContextBuilder();
    }

    public static long measureTime(Runnable action) {
        long start = System.currentTimeMillis();
        action.run();
        return System.currentTimeMillis() - start;
    }

    public static Map<String, Object> capturePerformanceMetrics(Runnable action) {
        Map<String, Object> metrics = new HashMap<>();
        metrics.put("timestamp", Instant.now().toString());

        long start = System.currentTimeMillis();
        try {
            action.run();
            metrics.put("success", true);
        } catch (Exception e) {
            metrics.put("success", false);
            metrics.put("error", e.getMessage());
        }

        long duration = System.currentTimeMillis() - start;
        metrics.put("duration", duration);

        return metrics;
    }

    public static String createAuditEntry(String action, String userId, Map<String, Object> details) {
        Map<String, Object> entry = new HashMap<>();
        entry.put("action", action);
        entry.put("userId", userId);
        entry.put("timestamp", Instant.now().toString());
        entry.putAll(details);

        return formatAsJson("AUDIT", entry);
    }

    public static String createChangeAuditEntry(String action, String userId,
                                                Map<String, Object> before,
                                                Map<String, Object> after) {
        Map<String, Object> entry = new HashMap<>();
        entry.put("action", action);
        entry.put("userId", userId);
        entry.put("timestamp", Instant.now().toString());
        entry.put("before", before);
        entry.put("after", after);

        return formatAsJson("CHANGE_AUDIT", entry);
    }

    public static Map<String, Object> maskSensitiveData(Map<String, Object> data) {
        Map<String, Object> masked = new HashMap<>();

        for (Map.Entry<String, Object> entry : data.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();

            if (value == null) {
                masked.put(key, null);
            } else if (isSensitiveField(key)) {
                masked.put(key, maskValue(key, value.toString()));
            } else {
                masked.put(key, value);
            }
        }

        return masked;
    }

    private static boolean isSensitiveField(String fieldName) {
        String lower = fieldName.toLowerCase();
        return lower.contains("password") ||
               lower.contains("email") ||
               lower.contains("creditcard") ||
               lower.contains("ssn") ||
               lower.contains("token") ||
               lower.contains("secret");
    }

    private static String maskValue(String fieldName, String value) {
        String lower = fieldName.toLowerCase();

        if (lower.contains("password") || lower.contains("secret") || lower.contains("token")) {
            return "***";
        } else if (lower.contains("email")) {
            int atIndex = value.indexOf('@');
            if (atIndex > 1) {
                return value.charAt(0) + "***" + value.substring(atIndex);
            }
            return "***";
        } else if (lower.contains("creditcard")) {
            if (value.length() >= 16) {
                return value.substring(0, 4) + "-****-****-" + value.substring(value.length() - 4);
            }
            return "***";
        }

        return "***";
    }

    public static class LogContextBuilder {
        private final Map<String, Object> fields = new HashMap<>();

        public LogContextBuilder add(String key, Object value) {
            fields.put(key, value);
            return this;
        }

        public LogContextBuilder addAll(Map<String, Object> values) {
            fields.putAll(values);
            return this;
        }

        public LogContext build() {
            return new LogContext(fields);
        }
    }
}