package ch.batbern.events.dto;

import jakarta.validation.constraints.*;
import lombok.*;

import java.util.UUID;

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

    private Integer eventNumber;

    private String date; // ISO 8601 format string (event_date)

    private String registrationDeadline; // ISO 8601 format string

    private String venueName;

    private String venueAddress;

    private Integer venueCapacity;

    @Pattern(regexp = "planning|topic_defined|speakers_invited|agenda_draft|published|registration_open|registration_closed|in_progress|completed|archived",
             message = "Status must be a valid workflow state")
    private String status;

    private UUID organizerId;

    private Integer currentAttendeeCount;

    private String publishedAt; // ISO 8601 format string

    private String metadata; // JSONB string

    @Size(max = 5000, message = "Description must not exceed 5000 characters")
    private String description;
}
