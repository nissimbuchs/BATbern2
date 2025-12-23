package ch.batbern.events.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for updating task status (drag-and-drop).
 * Story 5.5: Manual status transitions via drag-and-drop
 *
 * Allows transitions: pending ↔ todo ↔ completed
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateTaskStatusRequest {

    /**
     * New task status (pending | todo | in_progress | completed)
     */
    @NotBlank(message = "Status is required")
    @Pattern(
        regexp = "pending|todo|in_progress|completed",
        message = "Status must be one of: pending, todo, in_progress, completed"
    )
    private String status;
}
