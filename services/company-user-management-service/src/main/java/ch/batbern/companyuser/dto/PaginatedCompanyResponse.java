package ch.batbern.companyuser.dto;

import ch.batbern.shared.api.PaginationMetadata;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Paginated response wrapper for company queries
 * AC14: Support pagination with metadata
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaginatedCompanyResponse {
    /**
     * List of companies for current page
     */
    private List<CompanyResponse> data;

    /**
     * Pagination metadata (page, limit, total, hasNext, hasPrev)
     */
    private PaginationMetadata pagination;
}
