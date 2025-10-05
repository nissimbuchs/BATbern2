package ch.batbern.shared.dto;

import ch.batbern.shared.api.PaginationMetadata;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Standard paginated API response wrapper.
 *
 * Wraps collection responses with pagination metadata for consistent API responses.
 *
 * Example JSON:
 * {
 *   "data": [
 *     {"id": "1", "title": "Item 1"},
 *     {"id": "2", "title": "Item 2"}
 *   ],
 *   "pagination": {
 *     "page": 1,
 *     "limit": 20,
 *     "total": 45,
 *     "totalPages": 3,
 *     "hasNext": true,
 *     "hasPrev": false
 *   }
 * }
 *
 * @param <T> the type of items in the data list
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PaginatedResponse<T> {
    /**
     * The list of items for the current page.
     */
    private List<T> data;

    /**
     * Pagination metadata (page, limit, total, etc.).
     */
    private PaginationMetadata pagination;
}
