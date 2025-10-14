package ch.batbern.events.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * DTO for batch update request item
 * Story 1.15a.1: Events API Consolidation - AC14
 *
 * NOTE: Batch update only supports a limited subset of fields for partial updates
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchUpdateRequest {
    @NotNull(message = "Event ID is required")
    private UUID id;

    private String title;
    private String date;  // ISO-8601 format for event_date
    private String status;
    private String description;
}
