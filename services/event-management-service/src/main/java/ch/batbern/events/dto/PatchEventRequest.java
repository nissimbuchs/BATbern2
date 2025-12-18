package ch.batbern.events.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for partially updating an event (PATCH)
 * Story 1.16.2: Eliminate UUIDs from API
 * Uses String username instead of UUID for organizer
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

    private String organizerUsername;

    private Integer currentAttendeeCount;

    private String publishedAt; // ISO 8601 format string

    private String metadata; // JSONB string

    @Size(max = 5000, message = "Description must not exceed 5000 characters")
    private String description;

    private String themeImageUploadId;

    @Pattern(regexp = "AFTERNOON|EVENING|FULL_DAY",
             message = "Event type must be AFTERNOON, EVENING, or FULL_DAY")
    private String eventType;
}
