package ch.batbern.events.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Request DTO for updating an existing task (patch semantics).
 *
 * All fields are optional (null = keep existing value).
 * Supports editing notes, due date, and assigned organizer.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateEventTaskRequest {

    /**
     * Updated task notes (null = keep existing).
     */
    private String notes;

    /**
     * Updated due date (null = keep existing).
     */
    private Instant dueDate;

    /**
     * Updated assigned organizer username (null = keep existing, empty string = unassign).
     */
    private String assignedOrganizerUsername;
}
