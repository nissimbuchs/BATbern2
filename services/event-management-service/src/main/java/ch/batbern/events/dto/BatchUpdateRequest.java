package ch.batbern.events.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for batch update request item
 * Story 1.15a.1: Events API Consolidation - AC14
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchUpdateRequest {
    @NotNull(message = "Event ID is required")
    private String id;

    private String title;
    private String date;
    private String status;
    private String description;
    private String venueId;
}
