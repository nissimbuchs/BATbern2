package ch.batbern.events.dto;

import jakarta.validation.constraints.*;
import lombok.*;

/**
 * Request DTO for partially updating an event (PATCH)
 * Story 1.15a.1: Events API Consolidation - AC5
 *
 * All fields are optional - only provided fields will be updated
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PatchEventRequest {

    @Size(max = 200, message = "Title must not exceed 200 characters")
    private String title;

    private String date; // ISO 8601 format string

    @Pattern(regexp = "draft|published|archived|cancelled", message = "Status must be one of: draft, published, archived, cancelled")
    private String status;

    @Size(max = 5000, message = "Description must not exceed 5000 characters")
    private String description;

    private String venueId;
}
