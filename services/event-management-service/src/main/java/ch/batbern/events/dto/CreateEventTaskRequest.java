package ch.batbern.events.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Request DTO for creating an ad-hoc event task (Story 5.5 AC22).
 *
 * Used when an organizer creates a one-off task not from a template.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateEventTaskRequest {

    /**
     * Task name (required).
     * AC22: Free text task name
     */
    @NotBlank(message = "Task name is required")
    private String taskName;

    /**
     * Trigger state (required).
     * The workflow state associated with this task
     */
    @NotBlank(message = "Trigger state is required")
    private String triggerState;

    /**
     * Due date (optional).
     * When the task should be completed
     */
    private Instant dueDate;

    /**
     * Assigned organizer username (optional).
     * AC22, AC27: Can be assigned immediately or left unassigned
     */
    private String assignedOrganizerUsername;

    /**
     * Notes (optional).
     * Additional context or instructions for the task
     */
    private String notes;
}
