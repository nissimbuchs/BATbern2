package ch.batbern.events.dto;

import ch.batbern.shared.types.SpeakerWorkflowState;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Request DTO for updating speaker status
 * Story 5.4: Speaker Status Management - AC1-4
 */
@Data
public class UpdateStatusRequest {

    @NotNull(message = "New status is required")
    private SpeakerWorkflowState newStatus;

    @Size(max = 2000, message = "Reason must not exceed 2000 characters")
    private String reason;
}
