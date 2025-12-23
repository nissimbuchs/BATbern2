package ch.batbern.events.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for quality review approval or rejection.
 * Story 5.5 AC13-14: Quality Review
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewRequest {

    /**
     * Review action: "approve" or "reject"
     */
    @NotNull(message = "Action is required (approve or reject)")
    private ReviewAction action;

    /**
     * Feedback for rejection (required when action is reject, optional for approve)
     */
    private String feedback;

    /**
     * Review action enum
     */
    public enum ReviewAction {
        APPROVE,
        REJECT
    }
}
