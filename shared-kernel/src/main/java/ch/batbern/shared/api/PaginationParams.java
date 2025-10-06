package ch.batbern.shared.api;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Pagination parameters parsed from request.
 *
 * Used for querying paginated data from database.
 * Includes helper method to calculate offset for SQL LIMIT/OFFSET queries.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaginationParams {
    /**
     * Page number (1-indexed).
     * Default: 1
     */
    private int page;

    /**
     * Number of items per page.
     * Default: 20
     * Maximum: 100
     */
    private int limit;

    /**
     * Calculates offset for SQL LIMIT/OFFSET queries.
     *
     * @return Offset value (0-indexed)
     */
    public int getOffset() {
        return (page - 1) * limit;
    }
}
