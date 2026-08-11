package ch.batbern.shared.api;

import ch.batbern.shared.exception.ValidationException;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Parses sort strings into SortCriteria objects.
 *
 * Syntax:
 * - Ascending: "field" or "+field"
 * - Descending: "-field"
 * - Multiple fields: "-votes,+createdAt,title"
 * - Nested fields: "author.name", "-event.date"
 *
 * Examples:
 * - "createdAt" → [{field: "createdAt", direction: ASC}]
 * - "-votes" → [{field: "votes", direction: DESC}]
 * - "-votes,+createdAt" → [{field: "votes", direction: DESC}, {field: "createdAt", direction: ASC}]
 *
 * @see SortCriteria
 * @see SortDirection
 */
@Slf4j
public class SortParser {

    /**
     * Field names we are willing to hand to the persistence layer: a Java-style
     * identifier, optionally dot-separated for nested paths ("author.name").
     *
     * <p>Anything else is rejected here rather than downstream. Spring Data JPA's
     * {@code QueryUtils.checkSortExpression} rejects any {@code \p{Punct}} character in a
     * sort property by throwing {@code InvalidDataAccessApiUsageException} — and it does so
     * <em>before</em> resolving the property, so even a real field with a stray punctuation
     * character (e.g. {@code "date%"}) blows up. That exception is not a
     * {@code PropertyReferenceException}, so it escaped the 400 mapping and surfaced as a
     * 500 with severity CRITICAL (issue #903).
     *
     * <p>Validating at the parse boundary keeps malformed user input a 400 for every caller
     * of this parser, and keeps punctuation — the SQL-injection vector Spring Data's guard
     * exists to stop — out of sort expressions entirely.
     */
    private static final Pattern SAFE_FIELD_NAME =
            Pattern.compile("[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)*");

    /**
     * Parses a sort string into a list of SortCriteria.
     *
     * @param sortStr Sort string (e.g., "-votes,+createdAt")
     * @return List of SortCriteria in order, or empty list if sort is null/empty
     * @throws ValidationException if sort format is invalid
     */
    public static List<SortCriteria> parse(String sortStr) {
        // Handle null or empty sort
        if (sortStr == null || sortStr.trim().isEmpty()) {
            return Collections.emptyList();
        }

        List<SortCriteria> sortList = new ArrayList<>();

        // Split by comma and parse each field
        String[] fields = sortStr.split(",");

        for (String fieldStr : fields) {
            fieldStr = fieldStr.trim();

            if (fieldStr.isEmpty()) {
                continue; // Skip empty entries from trailing commas
            }

            SortCriteria criteria = parseField(fieldStr);
            sortList.add(criteria);
        }

        return sortList;
    }

    /**
     * Parses a single field string into SortCriteria.
     *
     * @param fieldStr Field string (e.g., "-votes", "+createdAt", "title")
     * @return SortCriteria object
     * @throws ValidationException if format is invalid
     */
    private static SortCriteria parseField(String fieldStr) {
        SortDirection direction = SortDirection.ASC; // Default to ascending
        String fieldName;

        // Check for direction prefix
        if (fieldStr.startsWith("-")) {
            direction = SortDirection.DESC;
            fieldName = fieldStr.substring(1);
        } else if (fieldStr.startsWith("+")) {
            direction = SortDirection.ASC;
            fieldName = fieldStr.substring(1);
        } else {
            fieldName = fieldStr;
        }

        // Validate field name
        if (fieldName.isEmpty()) {
            throw new ValidationException("Empty field name in sort string");
        }

        // Check for invalid characters (multiple prefixes)
        if (fieldName.startsWith("+") || fieldName.startsWith("-")) {
            throw new ValidationException("Invalid sort format: multiple prefix symbols");
        }

        // Reject anything that is not a plain identifier path (issue #903).
        // The field name is echoed back so the caller can see what was rejected; it is
        // user-supplied but safe here because ValidationException messages are rendered as
        // JSON text, never as a format string or SQL fragment.
        if (!SAFE_FIELD_NAME.matcher(fieldName).matches()) {
            throw new ValidationException("Invalid field name in sort: " + fieldName);
        }

        return SortCriteria.builder()
                .field(fieldName)
                .direction(direction)
                .build();
    }

    /**
     * Converts a list of SortCriteria to SQL ORDER BY clause.
     *
     * @param sortList List of sort criteria
     * @return SQL ORDER BY clause (e.g., "votes DESC, createdAt ASC") or empty string
     */
    public static String toSql(List<SortCriteria> sortList) {
        if (sortList == null || sortList.isEmpty()) {
            return "";
        }

        return sortList.stream()
                .map(SortCriteria::toSql)
                .collect(Collectors.joining(", "));
    }
}
