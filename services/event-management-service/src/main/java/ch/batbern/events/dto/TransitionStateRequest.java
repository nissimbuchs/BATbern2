package ch.batbern.events.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for transitioning an event to a new workflow state
 * Story 5.1a: Workflow State Machine Foundation - AC12
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TransitionStateRequest {

    @NotBlank(message = "Target state is required")
    @Pattern(
        regexp = "CREATED|TOPIC_SELECTION|SPEAKER_BRAINSTORMING|SPEAKER_OUTREACH|SPEAKER_CONFIRMATION|"
                 + "CONTENT_COLLECTION|QUALITY_REVIEW|THRESHOLD_CHECK|OVERFLOW_MANAGEMENT|SLOT_ASSIGNMENT|"
                 + "AGENDA_PUBLISHED|AGENDA_FINALIZED|NEWSLETTER_SENT|EVENT_READY|PARTNER_MEETING_COMPLETE|ARCHIVED",
        message = "Target state must be a valid EventWorkflowState"
    )
    private String targetState;
}
