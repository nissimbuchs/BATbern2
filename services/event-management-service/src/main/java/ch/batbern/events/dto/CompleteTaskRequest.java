package ch.batbern.events.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for completing a task (Story 5.5 AC25).
 *
 * Used when an organizer marks a task as complete with optional completion notes.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompleteTaskRequest {

    /**
     * Completion notes (optional).
     * AC25: Notes are stored in event_tasks.notes
     */
    private String notes;
}
