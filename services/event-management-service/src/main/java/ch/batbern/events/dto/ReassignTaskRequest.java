package ch.batbern.events.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for reassigning a task to a different organizer (Story 5.5 AC27).
 *
 * Used when transferring task ownership to another organizer.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReassignTaskRequest {

    /**
     * New organizer username (required).
     * AC27: Can assign to any organizer or leave unassigned
     */
    @NotBlank(message = "New organizer username is required")
    private String newOrganizerUsername;
}
