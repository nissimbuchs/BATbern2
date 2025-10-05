package ch.batbern.shared.api;

import ch.batbern.shared.exception.ValidationException;
import lombok.extern.slf4j.Slf4j;

/**
 * Utility class for pagination parameter parsing and metadata generation.
 *
 * Default values:
 * - page: 1 (first page, 1-indexed)
 * - limit: 20 (items per page)
 * - maxLimit: 100 (maximum items per page)
 *
 * Usage:
 * 1. Parse parameters from request: PaginationParams params = PaginationUtils.parseParams(page, limit)
 * 2. Query database with: LIMIT params.getLimit() OFFSET params.getOffset()
 * 3. Generate metadata: PaginationMetadata meta = PaginationUtils.generateMetadata(page, limit, totalCount)
 * 4. Return in response: {"data": [...], "pagination": meta}
 */
@Slf4j
public class PaginationUtils {

    // Configuration constants
    public static final int DEFAULT_PAGE = 1;
    public static final int DEFAULT_LIMIT = 20;
    public static final int MAX_LIMIT = 100;

    /**
     * Parses pagination parameters from request.
     *
     * @param page Page number (1-indexed), null for default
     * @param limit Items per page, null for default
     * @return Validated PaginationParams
     * @throws ValidationException if page or limit is invalid
     */
    public static PaginationParams parseParams(Integer page, Integer limit) {
        // Apply defaults
        int finalPage = (page != null) ? page : DEFAULT_PAGE;
        int finalLimit = (limit != null) ? limit : DEFAULT_LIMIT;

        // Validate page
        if (finalPage <= 0) {
            throw new ValidationException("Page number must be positive (got: " + finalPage + ")");
        }

        // Validate limit
        if (finalLimit <= 0) {
            throw new ValidationException("Limit must be positive (got: " + finalLimit + ")");
        }

        // Enforce max limit
        if (finalLimit > MAX_LIMIT) {
            log.debug("Requested limit {} exceeds maximum {}, capping at max", finalLimit, MAX_LIMIT);
            finalLimit = MAX_LIMIT;
        }

        return PaginationParams.builder()
                .page(finalPage)
                .limit(finalLimit)
                .build();
    }

    /**
     * Generates pagination metadata for response.
     *
     * @param page Current page number (1-indexed)
     * @param limit Items per page
     * @param total Total number of items across all pages
     * @return PaginationMetadata with calculated fields
     */
    public static PaginationMetadata generateMetadata(int page, int limit, long total) {
        // Calculate total pages
        int totalPages = (int) Math.ceil((double) total / limit);

        // Calculate navigation flags
        boolean hasNext = page < totalPages;
        boolean hasPrev = page > 1;

        return PaginationMetadata.builder()
                .page(page)
                .limit(limit)
                .total(total)
                .totalPages(totalPages)
                .hasNext(hasNext)
                .hasPrev(hasPrev)
                .build();
    }

    /**
     * Generates pagination metadata from PaginationParams and total count.
     *
     * @param params Pagination parameters
     * @param total Total number of items
     * @return PaginationMetadata
     */
    public static PaginationMetadata generateMetadata(PaginationParams params, long total) {
        return generateMetadata(params.getPage(), params.getLimit(), total);
    }
}
