package ch.batbern.events.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for creating a custom task template (Story 5.5 AC22, AC26).
 *
 * Used when an organizer creates a reusable task template for event planning.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateTaskTemplateRequest {

    /**
     * Template name (required).
     * AC22: Free text task name
     */
    @NotBlank(message = "Template name is required")
    private String name;

    /**
     * Trigger state (required).
     * AC22: Dropdown of all event workflow states
     * Must be one of: event_draft, topic_selection, speaker_brainstorming, etc.
     */
    @NotBlank(message = "Trigger state is required")
    private String triggerState;

    /**
     * Due date type (required).
     * AC22: immediate, relative_to_event, or absolute
     */
    @NotBlank(message = "Due date type is required")
    private String dueDateType;

    /**
     * Offset days for relative due dates (optional).
     * AC22: Negative = before event, positive = after event
     * Required when dueDateType is "relative_to_event"
     */
    private Integer dueDateOffsetDays;

    /**
     * Whether to save as reusable template (optional, default false).
     * AC22: "Save as template" checkbox
     */
    private Boolean saveAsTemplate;
}
