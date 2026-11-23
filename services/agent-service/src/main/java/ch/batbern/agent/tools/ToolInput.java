package ch.batbern.agent.tools;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Reads and checks a tool's input. Ids are ADR-003 meaningful ids with the patterns of the specs;
 * a value that fails is {@link InvalidToolInputException}, which a tool turns into an error result
 * without calling anything.
 */
public final class ToolInput {

    /** {@code eventCode} as the event specs declare it. */
    public static final Pattern EVENT_CODE = Pattern.compile("^BATbern[0-9]+$");
    /** {@code sessionSlug} as the sessions spec declares it. */
    public static final Pattern SESSION_SLUG = Pattern.compile("^[a-z0-9]+(-[a-z0-9]+)*$");

    private ToolInput() {
    }

    /** A required text field matching {@code pattern}. */
    public static String requiredId(JsonNode input, String field, Pattern pattern) {
        JsonNode value = input == null ? null : input.get(field);
        if (value == null || !value.isTextual() || !pattern.matcher(value.asText()).matches()) {
            throw new InvalidToolInputException(field + " is required and must match " + pattern.pattern());
        }
        return value.asText();
    }

    /** An optional integer field in {@code [min, max]}; empty when absent or null. */
    public static Optional<Integer> optionalInt(JsonNode input, String field, int min, int max) {
        JsonNode value = input == null ? null : input.get(field);
        if (value == null || value.isNull()) {
            return Optional.empty();
        }
        if (!value.canConvertToInt() || !value.isIntegralNumber() || value.asInt() < min || value.asInt() > max) {
            throw new InvalidToolInputException(field + " must be an integer from " + min + " to " + max);
        }
        return Optional.of(value.asInt());
    }

    /** An optional text field of 1 to {@code maxLength} characters after trimming; empty when absent, null or blank. */
    public static Optional<String> optionalText(JsonNode input, String field, int maxLength) {
        JsonNode value = input == null ? null : input.get(field);
        if (value == null || value.isNull()) {
            return Optional.empty();
        }
        if (!value.isTextual() || value.asText().strip().length() > maxLength) {
            throw new InvalidToolInputException(field + " must be text of at most " + maxLength + " characters");
        }
        String text = value.asText().strip();
        return text.isEmpty() ? Optional.empty() : Optional.of(text);
    }

    /** Input the tool refuses; its message is for the model and names the field, never the value. */
    public static final class InvalidToolInputException extends RuntimeException {
        public InvalidToolInputException(String message) {
            super(message);
        }
    }
}
