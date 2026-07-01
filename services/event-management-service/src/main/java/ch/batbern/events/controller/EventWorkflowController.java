package ch.batbern.events.controller;

import ch.batbern.events.core.api.generated.EventWorkflowApi;
import ch.batbern.events.core.dto.generated.TransitionStateRequest;
import ch.batbern.events.core.dto.generated.WorkflowStatusDto;
import ch.batbern.events.core.dto.generated.WorkflowTransitionResponse;
import ch.batbern.events.domain.Event;
import ch.batbern.events.exception.EventNotFoundException;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.security.SecurityContextHelper;
import ch.batbern.events.service.EventWorkflowStateMachine;
import ch.batbern.events.service.WorkflowTransitionValidator;
import ch.batbern.shared.types.EventWorkflowState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

/**
 * Event Workflow Controller
 * Story 5.1a: Workflow State Machine Foundation - AC12-13
 *
 * <p>Phase 7 (ADR-006 / api-consolidation): contract-first — this controller
 * {@code implements} the generated {@code EventWorkflowApi} interface (events-core spec,
 * "Event Workflow" tag), which carries the HTTP method/path mappings and request/response
 * DTO types. The class-level {@code @RequestMapping("/api/v1")} supplies the version prefix
 * the interface paths omit (e.g. interface {@code /events/{eventCode}/workflow/status} →
 * {@code /api/v1/events/...}). Method-level {@code @PreAuthorize} / cache eviction stay on the
 * implementation (the generated interface carries neither); behaviour matches the pre-wiring
 * controller exactly.
 *
 * Security:
 * - Authentication: JWT token required (enforced by Spring Security)
 * - Authorization: ORGANIZER role required for workflow transitions
 * - Rate Limiting: Applied at API Gateway level (10 transitions/min per user)
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Slf4j
public class EventWorkflowController implements EventWorkflowApi {

    private final EventWorkflowStateMachine stateMachine;
    private final WorkflowTransitionValidator transitionValidator;
    private final EventRepository eventRepository;
    private final SecurityContextHelper securityContextHelper;

    /**
     * Transition event to target workflow state (AC12).
     * POST /api/v1/events/{eventCode}/workflow/transition — requires ORGANIZER role,
     * rate-limited to 10 transitions/min (API Gateway). Username from JWT for audit.
     */
    @Override
    @PreAuthorize("hasRole('ORGANIZER')")
    @org.springframework.cache.annotation.Caching(evict = {
        @CacheEvict(value = "eventWithIncludes", allEntries = true),
        @CacheEvict(value = "archiveEvents", allEntries = true)
    })
    public ResponseEntity<WorkflowTransitionResponse> transitionEventWorkflowState(
            String eventCode, TransitionStateRequest transitionStateRequest) {

        // Extract authenticated user from JWT token
        String organizerUsername = securityContextHelper.getCurrentUsername();

        // Extract override flag (defaults to false if not provided)
        boolean override = Boolean.TRUE.equals(transitionStateRequest.getOverrideValidation());

        // Log override attempts for security auditing
        if (override) {
            log.warn("WORKFLOW OVERRIDE: user={}, event={}, target={}, reason='{}'",
                     organizerUsername, eventCode, transitionStateRequest.getTargetState(),
                     transitionStateRequest.getOverrideReason() != null
                             ? transitionStateRequest.getOverrideReason() : "Not provided");
        } else {
            log.info("User {} transitioning event {} to state {}",
                     organizerUsername, eventCode, transitionStateRequest.getTargetState());
        }

        // Find event by event code
        Event event = eventRepository.findByEventCode(eventCode)
                .orElseThrow(() -> new EventNotFoundException("Event not found: " + eventCode));

        // Parse target state
        EventWorkflowState targetState = EventWorkflowState.valueOf(transitionStateRequest.getTargetState());

        // Perform transition via state machine with override flag
        Event updatedEvent = stateMachine.transitionToState(
                event.getEventCode(),
                targetState,
                organizerUsername,
                override,
                transitionStateRequest.getOverrideReason()
        );

        WorkflowTransitionResponse response = new WorkflowTransitionResponse()
                .eventCode(updatedEvent.getEventCode())
                .workflowState(updatedEvent.getWorkflowState().name())
                .updatedAt(updatedEvent.getUpdatedAt() != null
                        ? updatedEvent.getUpdatedAt().atOffset(ZoneOffset.UTC) : null);

        log.info("Event {} successfully transitioned to {}", eventCode, targetState);

        return ResponseEntity.ok(response);
    }

    /**
     * Get current workflow status (AC13).
     * GET /api/v1/events/{eventCode}/workflow/status — requires authentication.
     * Returns current state, next available states, and validation blockers.
     */
    @Override
    public ResponseEntity<WorkflowStatusDto> getWorkflowStatus(String eventCode) {

        String requestingUser = securityContextHelper.getCurrentUsername();
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

        WorkflowStatusDto statusDto = new WorkflowStatusDto()
                .currentState(currentState.name())
                .nextAvailableStates(nextAvailableStates)
                .validationMessages(validationMessages)
                .blockedTransitions(blockedTransitions);

        log.info("Workflow status for event {}: current={}, next={}",
                 eventCode, currentState, nextAvailableStates);

        return ResponseEntity.ok(statusDto);
    }

    /**
     * Get next available states based on current state.
     * Delegates to WorkflowTransitionValidator for single source of truth; filters out
     * idempotent (same-state) transitions for UI display.
     */
    private List<String> getNextAvailableStates(EventWorkflowState currentState) {
        return transitionValidator.getValidTargetStates(currentState).stream()
                .filter(state -> state != currentState)
                .map(Enum::name)
                .toList();
    }
}
