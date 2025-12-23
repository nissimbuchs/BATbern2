package ch.batbern.events.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;
import java.util.UUID;

/**
 * Request to create tasks from templates for an event (Story 5.5 AC21).
 *
 * Used when creating or editing an event to generate tasks from selected templates.
 * Each template can have an optional assigned organizer.
 */
@Data
public class CreateTasksFromTemplatesRequest {

    /**
     * List of template configurations (template ID + optional assignee).
     */
    @NotEmpty(message = "Template list cannot be empty")
    @Valid
    private List<TemplateConfig> templates;

    /**
     * Template configuration entry.
     */
    @Data
    public static class TemplateConfig {
        /**
         * Template ID to create task from.
         */
        @NotNull(message = "Template ID is required")
        private UUID templateId;

        /**
         * Optional organizer username to assign the task to.
         */
        private String assignedOrganizerUsername;
    }
}
