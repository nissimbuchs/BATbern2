package ch.batbern.gateway.util;

/**
 * Utility class to sanitize user input before logging
 *
 * Prevents log injection attacks by removing/encoding newlines and carriage returns
 * that could be used to inject fake log entries.
 */
public final class LogSanitizer {

    private LogSanitizer() {
        // Utility class - prevent instantiation
    }

    /**
     * Sanitizes a string for safe logging by removing newlines and carriage returns
     *
     * @param input the input string to sanitize
     * @return sanitized string safe for logging, or "null" if input is null
     */
    public static String sanitize(String input) {
        if (input == null) {
            return "null";
        }

        // Replace newlines, carriage returns, and tabs with spaces
        return input
                .replace('\n', ' ')
                .replace('\r', ' ')
                .replace('\t', ' ');
    }

    /**
     * Sanitizes an object's toString() output for safe logging
     *
     * @param input the object to sanitize
     * @return sanitized string safe for logging
     */
    public static String sanitize(Object input) {
        if (input == null) {
            return "null";
        }
        return sanitize(input.toString());
    }
}
