package ch.batbern.shared.api;

import ch.batbern.shared.exception.ValidationException;
import lombok.extern.slf4j.Slf4j;

import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Parses fields parameter for sparse fieldsets (field selection).
 *
 * Allows clients to request only specific fields in the response,
 * reducing payload size and improving performance.
 *
 * Syntax: "id,title,votes" or "id,author.name,createdAt"
 * Supports: Nested fields with dot notation
 *
 * Returns:
 * - null if no fields parameter (return all fields)
 * - Set<String> of requested field names
 *
 * Example:
 * - "id,title" → Only return id and title
 * - "author.name,event.date" → Return nested fields
 * - null → Return all fields
 */
@Slf4j
public class FieldSelector {

    // Field name pattern: alphanumeric, underscore, dot (for nesting)
    private static final Pattern VALID_FIELD_PATTERN = Pattern.compile("^[a-zA-Z0-9_.]+$");

    /**
     * Parses fields string into a set of field names.
     *
     * @param fieldsStr Comma-separated field names (e.g., "id,title,author.name")
     * @return Set of field names, or null if fieldsStr is null/empty (return all fields)
     * @throws ValidationException if field name is invalid
     */
    public static Set<String> parse(String fieldsStr) {
        // Null or empty means return all fields
        if (fieldsStr == null || fieldsStr.trim().isEmpty()) {
            return null;
        }

        // Split by comma and collect into set (automatically deduplicates)
        Set<String> fields = Arrays.stream(fieldsStr.split(","))
                .map(String::trim)
                .filter(field -> !field.isEmpty()) // Remove empty strings
                .collect(Collectors.toSet());

        // Validate each field name
        for (String field : fields) {
            if (!VALID_FIELD_PATTERN.matcher(field).matches()) {
                throw new ValidationException("Invalid field name: " + field +
                        " (must contain only alphanumeric characters, underscores, and dots)");
            }
        }

        return fields.isEmpty() ? null : fields;
    }
}
