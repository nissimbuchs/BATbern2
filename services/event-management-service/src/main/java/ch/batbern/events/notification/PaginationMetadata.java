package ch.batbern.events.notification;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Pagination metadata for notification responses
 * Story BAT-7: Matches frontend API contract
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaginationMetadata {
    private int page;
    private int limit;
    private long totalItems;
    private int totalPages;

    /**
     * Convert from shared PaginationMetadata to notification-specific format
     */
    public static PaginationMetadata fromShared(ch.batbern.shared.api.PaginationMetadata shared) {
        return PaginationMetadata.builder()
                .page(shared.getPage())
                .limit(shared.getLimit())
                .totalItems(shared.getTotalItems())
                .totalPages(shared.getTotalPages())
                .build();
    }
}
