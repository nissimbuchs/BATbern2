package ch.batbern.events.controller;

import ch.batbern.events.domain.Event;
import ch.batbern.events.dto.TransitionStateRequest;
import ch.batbern.events.dto.WorkflowStatusDto;
import ch.batbern.events.exception.EventNotFoundException;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.security.SecurityContextHelper;
import ch.batbern.events.service.EventWorkflowStateMachine;
import ch.batbern.events.service.WorkflowTransitionValidator;
import ch.batbern.shared.types.EventWorkflowState;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Event Workflow Controller
 * Story 5.1a: Workflow State Machine Foundation - AC12-13
 *
 * Provides endpoints for managing event workflow state transitions
 *
 * Security:
 * - Authentication: JWT token required (enforced by Spring Security)
 * - Authorization: ORGANIZER role required for workflow transitions
 * - Rate Limiting: Applied at API Gateway level (10 transitions/min per user)
 */
@RestController
@RequestMapping("/api/v1/events")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Event Workflow", description = "Event workflow state management API")
public class EventWorkflowController {

    private final EventWorkflowStateMachine stateMachine;
    private final WorkflowTransitionValidator transitionValidator;
    private final EventRepository eventRepository;
    private final SecurityContextHelper securityContextHelper;

    /**
     * Transition event to target workflow state (AC12)
     *
     * PUT /api/v1/events/{code}/workflow/transition
     *
     * Security:
     * - Requires ORGANIZER role
     * - Rate limited to 10 transitions per minute (API Gateway)
     * - Username extracted from JWT token for audit trail
     *
     * @param eventCode Event code (e.g., "BAT-2024-Q4")
     * @param request Transition request containing target state
     * @return Updated event with new workflow state
     */
    @PutMapping("/{code}/workflow/transition")
    @PreAuthorize("hasRole('ORGANIZER')")
    @CacheEvict(value = "eventWithIncludes", allEntries = true)
    @Operation(summary = "Transition event to target workflow state",
               description = "Requires ORGANIZER role. Rate limited to 10 transitions/min.")
    public ResponseEntity<Map<String, Object>> transitionEventWorkflowState(
            @PathVariable("code") String eventCode,
            @Valid @RequestBody TransitionStateRequest request) {

        // Extract authenticated user from JWT token
        String organizerUsername = securityContextHelper.getCurrentUserId();

        // Extract override flag (defaults to false if not provided)
        boolean override = Boolean.TRUE.equals(request.getOverrideValidation());

        // Log override attempts for security auditing
        if (override) {
            log.warn("WORKFLOW OVERRIDE: user={}, event={}, target={}, reason='{}'",
                     organizerUsername, eventCode, request.getTargetState(),
                     request.getOverrideReason() != null ? request.getOverrideReason() : "Not provided");
        } else {
            log.info("User {} transitioning event {} to state {}",
                     organizerUsername, eventCode, request.getTargetState());
        }

        // Find event by event code
        Event event = eventRepository.findByEventCode(eventCode)
                .orElseThrow(() -> new EventNotFoundException("Event not found: " + eventCode));

        // Parse target state
        EventWorkflowState targetState = EventWorkflowState.valueOf(request.getTargetState());

        // Perform transition via state machine with override flag
        Event updatedEvent = stateMachine.transitionToState(
                event.getEventCode(),
                targetState,
                organizerUsername,
                override,
                request.getOverrideReason()
        );

        // Build response
        Map<String, Object> response = new HashMap<>();
        response.put("eventCode", updatedEvent.getEventCode());
        response.put("workflowState", updatedEvent.getWorkflowState().name());
        response.put("updatedAt", updatedEvent.getUpdatedAt());

        log.info("Event {} successfully transitioned to {}", eventCode, targetState);

        return ResponseEntity.ok(response);
    }

    /**
     * Get current workflow status (AC13)
     *
     * GET /api/v1/events/{code}/workflow/status
     *
     * Security:
     * - Requires authentication (any authenticated user)
     * - Rate limited at API Gateway level
     *
     * @param eventCode Event code (e.g., "BAT-2024-Q4")
     * @return Current workflow status with next available states and validation messages
     */
    @GetMapping("/{code}/workflow/status")
    @Operation(summary = "Get current workflow status",
               description = "Returns current state, next available states, and validation blockers")
    public ResponseEntity<WorkflowStatusDto> getWorkflowStatus(
            @PathVariable("code") String eventCode) {

        String requestingUser = securityContextHelper.getCurrentUserId();
        log.info("User {} querying workflow status for event {}", requestingUser, eventCode);

        // Find event by event code
        Event event = eventRepository.findByEventCode(eventCode)
                .orElseThrow(() -> new EventNotFoundException("Event not found: " + eventCode));

        EventWorkflowState currentState = event.getWorkflowState();

        // Get next available states
        List<String> nextAvailableStates = getNextAvailableStates(currentState);

        // Get validation messages (check which transitions are blocked)
        List<String> validationMessages = new ArrayList<>();
        List<String> blockedTransitions = new ArrayList<>();

        for (String nextState : nextAvailableStates) {
            try {
                EventWorkflowState targetState = EventWorkflowState.valueOf(nextState);
                transitionValidator.validateTransition(currentState, targetState, event);
            } catch (Exception e) {
                validationMessages.add(String.format("Cannot transition to %s: %s", nextState, e.getMessage()));
                blockedTransitions.add(nextState);
            }
        }

        WorkflowStatusDto statusDto = WorkflowStatusDto.builder()
                .currentState(currentState.name())
                .nextAvailableStates(nextAvailableStates)
                .validationMessages(validationMessages)
                .blockedTransitions(blockedTransitions)
                .build();

        log.info("Workflow status for event {}: current={}, next={}",
                 eventCode, currentState, nextAvailableStates);

        return ResponseEntity.ok(statusDto);
    }

    /**
     * Get next available states based on current state
     * This follows the 16-step workflow sequence
     */
    private List<String> getNextAvailableStates(EventWorkflowState currentState) {
        List<String> nextStates = new ArrayList<>();

        switch (currentState) {
            case CREATED:
                nextStates.add(EventWorkflowState.TOPIC_SELECTION.name());
                break;
            case TOPIC_SELECTION:
                nextStates.add(EventWorkflowState.SPEAKER_BRAINSTORMING.name());
                break;
            case SPEAKER_BRAINSTORMING:
                nextStates.add(EventWorkflowState.SPEAKER_OUTREACH.name());
                break;
            case SPEAKER_OUTREACH:
                nextStates.add(EventWorkflowState.SPEAKER_CONFIRMATION.name());
                break;
            case SPEAKER_CONFIRMATION:
                nextStates.add(EventWorkflowState.CONTENT_COLLECTION.name());
                break;
            case CONTENT_COLLECTION:
                nextStates.add(EventWorkflowState.QUALITY_REVIEW.name());
                break;
            case QUALITY_REVIEW:
                nextStates.add(EventWorkflowState.THRESHOLD_CHECK.name());
                break;
            case THRESHOLD_CHECK:
                nextStates.add(EventWorkflowState.OVERFLOW_MANAGEMENT.name());
                break;
            case OVERFLOW_MANAGEMENT:
                nextStates.add(EventWorkflowState.SLOT_ASSIGNMENT.name());
                break;
            case SLOT_ASSIGNMENT:
                nextStates.add(EventWorkflowState.AGENDA_PUBLISHED.name());
                break;
            case AGENDA_PUBLISHED:
                nextStates.add(EventWorkflowState.AGENDA_FINALIZED.name());
                break;
            case AGENDA_FINALIZED:
                nextStates.add(EventWorkflowState.NEWSLETTER_SENT.name());
                break;
            case NEWSLETTER_SENT:
                nextStates.add(EventWorkflowState.EVENT_READY.name());
                break;
            case EVENT_READY:
                nextStates.add(EventWorkflowState.PARTNER_MEETING_COMPLETE.name());
                break;
            case PARTNER_MEETING_COMPLETE:
                nextStates.add(EventWorkflowState.ARCHIVED.name());
                break;
            case ARCHIVED:
                // Terminal state - no next states
                break;
            default:
                // All enum values covered above
                break;
        }

        return nextStates;
    }
}
