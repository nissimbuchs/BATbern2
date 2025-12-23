package ch.batbern.events.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for updating a custom task template (Story 5.5 AC26).
 *
 * Used when an organizer updates an existing custom task template.
 * Default templates cannot be updated.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateTaskTemplateRequest {

    /**
     * Template name (required).
     */
    @NotBlank(message = "Template name is required")
    private String name;

    /**
     * Trigger state (required).
     * Must be one of the valid event workflow states
     */
    @NotBlank(message = "Trigger state is required")
    private String triggerState;

    /**
     * Due date type (required).
     * Must be: immediate, relative_to_event, or absolute
     */
    @NotBlank(message = "Due date type is required")
    private String dueDateType;

    /**
     * Offset days for relative due dates (optional).
     * Negative = before event, positive = after event
     * Required when dueDateType is "relative_to_event"
     */
    private Integer dueDateOffsetDays;
}
