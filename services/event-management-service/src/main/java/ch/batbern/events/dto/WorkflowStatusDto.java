package ch.batbern.events.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Response DTO for workflow status queries
 * Story 5.1a: Workflow State Machine Foundation - AC13
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WorkflowStatusDto {

    /**
     * Current workflow state of the event
     */
    private String currentState;

    /**
     * List of next available states that can be transitioned to from the current state
     */
    private List<String> nextAvailableStates;

    /**
     * List of validation messages indicating why certain transitions are blocked
     * Empty list if all transitions are available
     */
    private List<String> validationMessages;

    /**
     * List of blocked transitions with reasons
     * Empty list if no transitions are blocked
     */
    private List<String> blockedTransitions;
}
