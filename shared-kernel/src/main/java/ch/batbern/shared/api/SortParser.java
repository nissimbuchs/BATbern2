package ch.batbern.shared.api;

import ch.batbern.shared.exception.ValidationException;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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
