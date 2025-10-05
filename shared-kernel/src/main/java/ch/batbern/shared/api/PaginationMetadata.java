package ch.batbern.shared.api;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Pagination metadata included in API responses.
 *
 * Provides information about the current page, total results, and navigation.
 *
 * Example JSON:
 * {
 *   "page": 2,
 *   "limit": 20,
 *   "total": 145,
 *   "totalPages": 8,
 *   "hasNext": true,
 *   "hasPrev": true
 * }
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaginationMetadata {
    /**
     * Current page number (1-indexed).
     */
    private int page;

    /**
     * Number of items per page.
     */
    private int limit;

    /**
     * Total number of items across all pages.
     */
    private long total;

    /**
     * Total number of pages.
     */
    private int totalPages;

    /**
     * Whether there is a next page.
     */
    private boolean hasNext;

    /**
     * Whether there is a previous page.
     */
    private boolean hasPrev;
}
