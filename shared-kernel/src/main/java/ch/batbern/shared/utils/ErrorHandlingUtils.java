package ch.batbern.shared.utils;

import ch.batbern.shared.exceptions.DomainException;
import ch.batbern.shared.exceptions.ValidationException;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ErrorHandlingUtils {

    private ErrorHandlingUtils() {
        // Utility class, prevent instantiation
    }

    public static String formatErrorMessage(Exception exception) {
        return formatErrorMessage(exception, false);
    }

    public static String formatErrorMessage(Exception exception, boolean includeStackTrace) {
        StringBuilder sb = new StringBuilder();

        if (exception instanceof DomainException) {
            DomainException de = (DomainException) exception;
            sb.append(de.getErrorCode()).append(": ");
        } else if (exception instanceof ValidationException) {
            sb.append("VALIDATION_ERROR: ");
        }

        sb.append(exception.getMessage());

        if (exception.getCause() != null) {
            sb.append(" | Caused by: ").append(exception.getCause().getMessage());
        }

        if (includeStackTrace) {
            sb.append("\n").append(getStackTraceAsString(exception));
        }

        return sb.toString();
    }

    private static String getStackTraceAsString(Exception exception) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        exception.printStackTrace(pw);
        return sw.toString();
    }

    public static ErrorResponse createErrorResponse(Exception exception) {
        return createErrorResponse(exception, null);
    }

    public static ErrorResponse createErrorResponse(Exception exception, String path) {
        return createErrorResponse(exception, path, null);
    }

    public static ErrorResponse createErrorResponse(Exception exception, String path, String traceId) {
        String errorCode = "INTERNAL_ERROR";
        String message = exception.getMessage();

        if (exception instanceof DomainException) {
            errorCode = ((DomainException) exception).getErrorCode();
        } else if (exception instanceof ValidationException) {
            errorCode = "VALIDATION_ERROR";
        }

        ErrorResponse response = new ErrorResponse();
        response.setError(errorCode);
        response.setMessage(message);
        response.setTimestamp(Instant.now());
        response.setPath(path);
        response.setTraceId(traceId);

        return response;
    }

    public static ErrorResponse createValidationErrorResponse(Map<String, String> fieldErrors) {
        ErrorResponse response = new ErrorResponse();
        response.setError("VALIDATION_ERROR");
        response.setMessage("Validation failed for multiple fields");
        response.setTimestamp(Instant.now());
        response.setDetails(new HashMap<>(fieldErrors));
        return response;
    }

    public static boolean isClientError(Exception exception) {
        return exception instanceof ValidationException ||
               exception instanceof IllegalArgumentException;
    }

    public static boolean isServerError(Exception exception) {
        return !isClientError(exception);
    }

    public static boolean isRetryableError(Exception exception) {
        String message = exception.getMessage();
        if (message == null) {
            return false;
        }
        String lowerMessage = message.toLowerCase();
        return lowerMessage.contains("timeout") ||
               lowerMessage.contains("connection") ||
               lowerMessage.contains("temporarily unavailable");
    }

    public static ErrorResponse aggregateErrors(List<? extends Exception> errors) {
        ErrorResponse response = new ErrorResponse();
        response.setError("MULTIPLE_ERRORS");
        response.setMessage("Multiple errors occurred");
        response.setTimestamp(Instant.now());

        Map<String, Object> details = new HashMap<>();
        for (int i = 0; i < errors.size(); i++) {
            Exception error = errors.get(i);
            details.put("error_" + (i + 1), formatErrorMessage(error));
        }
        response.setDetails(details);

        return response;
    }

    public static Map<String, List<String>> groupErrorsByType(List<Exception> errors) {
        Map<String, List<String>> grouped = new HashMap<>();

        for (Exception error : errors) {
            String type = getErrorType(error);
            grouped.computeIfAbsent(type, k -> new ArrayList<>())
                   .add(error.getMessage());
        }

        return grouped;
    }

    private static String getErrorType(Exception exception) {
        if (exception instanceof ValidationException) {
            return "VALIDATION_ERROR";
        } else if (exception instanceof IllegalArgumentException) {
            return "ILLEGAL_ARGUMENT";
        } else if (exception instanceof RuntimeException) {
            return "RUNTIME_ERROR";
        }
        return "UNKNOWN_ERROR";
    }

    public static Exception enhanceWithContext(Exception original, Map<String, Object> context) {
        StringBuilder enhancedMessage = new StringBuilder(original.getMessage());

        if (!context.isEmpty()) {
            enhancedMessage.append(" | Context: ");
            String contextString = context.entrySet().stream()
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining(", "));
            enhancedMessage.append(contextString);
        }

        if (original.getCause() != null) {
            return new RuntimeException(enhancedMessage.toString(), original.getCause());
        }
        return new RuntimeException(enhancedMessage.toString());
    }
}