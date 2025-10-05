package ch.batbern.shared.api;

import ch.batbern.shared.exception.ValidationException;
import lombok.extern.slf4j.Slf4j;

import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Parses include parameter for resource expansion (sideloading related resources).
 *
 * Allows clients to request related resources to be included in the response,
 * reducing the number of API calls needed (N+1 query prevention).
 *
 * Syntax: "author,comments,venue" or "author,event.venue"
 * Supports: Nested includes with dot notation
 *
 * Returns: Set<String> of relation names to eagerly load
 *
 * Example:
 * - "author,comments" → Include author and comments in response
 * - "event.venue" → Include nested venue through event
 * - null/empty → Don't include any relations (lazy loading)
 */
@Slf4j
public class IncludeParser {

    // Relation name pattern: alphanumeric, underscore, dot (for nesting)
    private static final Pattern VALID_RELATION_PATTERN = Pattern.compile("^[a-zA-Z0-9_.]+$");

    /**
     * Parses include string into a set of relation names.
     *
     * @param includeStr Comma-separated relation names (e.g., "author,comments,venue")
     * @return Set of relation names to include, or empty set if includeStr is null/empty
     * @throws ValidationException if relation name is invalid
     */
    public static Set<String> parse(String includeStr) {
        // Null or empty means don't include any relations
        if (includeStr == null || includeStr.trim().isEmpty()) {
            return Collections.emptySet();
        }

        // Split by comma and collect into set (automatically deduplicates)
        Set<String> includes = Arrays.stream(includeStr.split(","))
                .map(String::trim)
                .filter(relation -> !relation.isEmpty()) // Remove empty strings
                .collect(Collectors.toSet());

        // Validate each relation name
        for (String relation : includes) {
            if (!VALID_RELATION_PATTERN.matcher(relation).matches()) {
                throw new ValidationException("Invalid relation name: " + relation +
                        " (must contain only alphanumeric characters, underscores, and dots)");
            }
        }

        return includes;
    }
}
