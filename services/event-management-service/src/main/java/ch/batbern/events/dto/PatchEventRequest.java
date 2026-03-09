package ch.batbern.events.dto;

import ch.batbern.shared.types.EventWorkflowState;
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

    private EventWorkflowState workflowState;

    // Story 10.4: Set topic code directly (bypasses workflow state machine — for blob selector accept flow)
    private String topicCode;

    // Story 10.4: Record selection rationale from blob selector session
    private String topicSelectionNote;

    // Story 10.11: nullable — when provided (non-null), sets the limit; to clear use PUT with null
    private Integer registrationCapacity;
}
