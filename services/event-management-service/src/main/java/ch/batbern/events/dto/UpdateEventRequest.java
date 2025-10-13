package ch.batbern.events.dto;

import jakarta.validation.constraints.*;
import lombok.*;

/**
 * Request DTO for updating an existing event (PUT - full replacement)
 * Story 1.15a.1: Events API Consolidation - AC4
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateEventRequest {

    @NotBlank(message = "Title is required")
    @Size(max = 200, message = "Title must not exceed 200 characters")
    private String title;

    @NotBlank(message = "Event date is required")
    private String date; // ISO 8601 format string

    @NotBlank(message = "Status is required")
    @Pattern(regexp = "draft|published|archived|cancelled", message = "Status must be one of: draft, published, archived, cancelled")
    private String status;

    @Size(max = 5000, message = "Description must not exceed 5000 characters")
    private String description;

    private String venueId;
}
