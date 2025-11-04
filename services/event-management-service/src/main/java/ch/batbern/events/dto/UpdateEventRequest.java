package ch.batbern.events.dto;

import jakarta.validation.constraints.*;
import lombok.*;

/**
 * Request DTO for updating an existing event (PUT - full replacement)
 * Story 1.16.2: Eliminate UUIDs from API
 * Uses String username instead of UUID for organizer
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateEventRequest {

    @NotBlank(message = "Title is required")
    @Size(max = 200, message = "Title must not exceed 200 characters")
    private String title;

    private Integer eventNumber;

    @NotBlank(message = "Event date is required")
    private String date; // ISO 8601 format string

    private String registrationDeadline; // ISO 8601 format string

    private String venueName;

    private String venueAddress;

    private Integer venueCapacity;

    @Pattern(regexp = "planning|topic_defined|speakers_invited|agenda_draft|published|registration_open|registration_closed|in_progress|completed|archived",
             message = "Status must be a valid workflow state")
    private String status;

    private String organizerUsername;

    private Integer currentAttendeeCount;

    private String publishedAt; // ISO 8601 format string

    private String metadata;

    @Size(max = 5000, message = "Description must not exceed 5000 characters")
    private String description;

    private String themeImageUploadId;
}
