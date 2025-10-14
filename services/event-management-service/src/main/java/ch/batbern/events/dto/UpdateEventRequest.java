package ch.batbern.events.dto;

import jakarta.validation.constraints.*;
import lombok.*;
import java.util.UUID;

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

    @NotNull(message = "Event number is required")
    private Integer eventNumber;

    @NotBlank(message = "Event date is required")
    private String date; // ISO 8601 format string

    @NotBlank(message = "Registration deadline is required")
    private String registrationDeadline; // ISO 8601 format string

    @NotBlank(message = "Venue name is required")
    private String venueName;

    @NotBlank(message = "Venue address is required")
    private String venueAddress;

    @NotNull(message = "Venue capacity is required")
    private Integer venueCapacity;

    @NotBlank(message = "Status is required")
    @Pattern(regexp = "planning|topic_defined|speakers_invited|agenda_draft|published|registration_open|registration_closed|in_progress|completed|archived",
             message = "Status must be a valid workflow state")
    private String status;

    @NotNull(message = "Organizer ID is required")
    private UUID organizerId;

    private Integer currentAttendeeCount;

    private String publishedAt; // ISO 8601 format string

    private String metadata;

    @Size(max = 5000, message = "Description must not exceed 5000 characters")
    private String description;
}
