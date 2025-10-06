package ch.batbern.shared.api;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents a sort criterion for ordering query results.
 *
 * Example:
 * - Field: "votes", Direction: DESC → ORDER BY votes DESC
 * - Field: "created At", Direction: ASC → ORDER BY createdAt ASC
 * - Field: "author.name", Direction: ASC → ORDER BY author.name ASC (nested field)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SortCriteria {
    /**
     * Field name to sort by (supports dot notation for nested fields).
     */
    private String field;

    /**
     * Sort direction (ASC or DESC).
     */
    private SortDirection direction;

    /**
     * Converts to SQL ORDER BY clause fragment.
     *
     * @return SQL string like "votes DESC" or "createdAt ASC"
     */
    public String toSql() {
        return field + " " + direction.name();
    }
}
