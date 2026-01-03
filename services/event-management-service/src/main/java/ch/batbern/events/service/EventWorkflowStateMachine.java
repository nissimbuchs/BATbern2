package ch.batbern.events.service;

import ch.batbern.events.domain.Event;
import ch.batbern.events.exception.WorkflowValidationException;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.shared.events.DomainEventPublisher;
import ch.batbern.shared.events.EventWorkflowTransitionEvent;
import ch.batbern.shared.types.EventWorkflowState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;

/**
 * State machine for managing event workflow transitions.
 *
 * This service orchestrates the 16-step event workflow, enforcing validation rules
 * and publishing domain events on successful transitions.
 *
 * Responsibilities:
 * - Validate state transitions using WorkflowTransitionValidator
 * - Apply state-specific business logic (e.g., minimum speakers required)
 * - Update event state in database
 * - Publish EventWorkflowTransitionEvent domain events
 * - Ensure transactional integrity with rollback on validation failures
 *
 * Story 5.1a: Workflow State Machine Foundation - AC4-11
 *
 * @see EventWorkflowState
 * @see WorkflowTransitionValidator
 * @see EventWorkflowTransitionEvent
 */
@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class EventWorkflowStateMachine {

    private final EventRepository eventRepository;
    private final WorkflowTransitionValidator transitionValidator;
    private final DomainEventPublisher eventPublisher;
    private final ch.batbern.events.repository.SpeakerPoolRepository speakerPoolRepository;
    private final ch.batbern.events.repository.SessionRepository sessionRepository;

    /**
     * Transitions an event to a target workflow state (backward compatible).
     *
     * This method delegates to the full override-aware version with override=false.
     *
     * @param eventCode Event code (e.g., "BATbern56")
     * @param targetState Target workflow state
     * @param organizerUsername Username of organizer triggering the transition
     * @return Updated event with new workflow state
     * @throws IllegalArgumentException if event not found
     * @throws InvalidStateTransitionException if transition not allowed
     * @throws WorkflowValidationException if business rules not met
     */
    public Event transitionToState(String eventCode, EventWorkflowState targetState, String organizerUsername) {
        return transitionToState(eventCode, targetState, organizerUsername, false, null);
    }

    /**
     * Transitions an event to a target workflow state with optional validation override.
     *
     * This method:
     * 1. Fetches the event from database
     * 2. Optionally validates the state transition (skipped if override=true)
     * 3. Optionally applies state-specific business logic validation (skipped if override=true)
     * 4. Updates the event state
     * 5. Persists the change
     * 6. Publishes a domain event with override metadata
     *
     * @param eventCode Event code (e.g., "BATbern56")
     * @param targetState Target workflow state
     * @param organizerUsername Username of organizer triggering the transition
     * @param override If true, skips all validation checks (allows any state transition)
     * @param overrideReason Optional reason for overriding validation (for audit trail)
     * @return Updated event with new workflow state
     * @throws IllegalArgumentException if event not found
     * @throws InvalidStateTransitionException if transition not allowed (when override=false)
     * @throws WorkflowValidationException if business rules not met (when override=false)
     */
    public Event transitionToState(
            String eventCode,
            EventWorkflowState targetState,
            String organizerUsername,
            boolean override,
            String overrideReason) {

        // Fetch event
        Event event = eventRepository.findByEventCode(eventCode)
                .orElseThrow(() -> new IllegalArgumentException("Event not found: " + eventCode));

        EventWorkflowState currentState = event.getWorkflowState();

        // Conditional validation: skip if override=true
        if (!override) {
            // Normal path: validate transition and business rules
            transitionValidator.validateTransition(currentState, targetState, event);
            validateBusinessRules(event, targetState);
            log.debug("Validation passed for {} → {}", currentState, targetState);
        } else {
            // Override path: skip ALL validation
            log.warn("OVERRIDE MODE: Skipping validation for {} → {} (user: {}, reason: '{}')",
                     currentState, targetState, organizerUsername,
                     overrideReason != null ? overrideReason : "Not provided");
        }

        // Update state
        event.setWorkflowState(targetState);
        event.setUpdatedBy(organizerUsername);
        event.setUpdatedAt(Instant.now());

        // Persist
        Event savedEvent = eventRepository.save(event);

        // Publish domain event with override metadata
        EventWorkflowTransitionEvent transitionEvent = new EventWorkflowTransitionEvent(
                eventCode,
                currentState,
                targetState,
                organizerUsername,
                Instant.now(),
                override,
                overrideReason
        );
        eventPublisher.publish(transitionEvent);

        log.info("Event {} transitioned from {} to {} by organizer {} (override={})",
                eventCode, currentState, targetState, organizerUsername, override);

        return savedEvent;
    }

    /**
     * Validates business rules for transitioning to a target state.
     *
     * Each state may have specific requirements that must be met before transition.
     * This method applies those validations.
     *
     * @param event Event being transitioned
     * @param targetState Target workflow state
     * @throws WorkflowValidationException if business rules not met
     */
    private void validateBusinessRules(Event event, EventWorkflowState targetState) {
        switch (targetState) {
            // 9-State Model: Removed SPEAKER_OUTREACH and QUALITY_REVIEW (consolidated into SPEAKER_IDENTIFICATION)
            case SLOT_ASSIGNMENT:
                validateMinimumThresholdMet(event);
                break;
            case AGENDA_FINALIZED:
                validateAllSlotsAssigned(event);
                break;
            case AGENDA_PUBLISHED:
                validateQualityReviewComplete(event);
                break;
            // Other states don't require additional validation
            default:
                // No additional validation required
                break;
        }
    }

    /**
     * Validates that minimum number of speakers have been identified.
     *
     * Required for transition to SPEAKER_OUTREACH.
     *
     * NOTE: This is a placeholder implementation for Story 5.1a.
     * Full implementation depends on Session/Speaker entities which will be
     * implemented in subsequent stories (5.3, 5.4).
     *
     * For now, this always throws an exception to satisfy TDD tests.
     * The exception will be removed once session/speaker tracking is implemented.
     *
     * @param event Event being validated
     * @throws WorkflowValidationException if insufficient speakers identified
     */
    private void validateMinimumSpeakersIdentified(Event event) {
        // Placeholder: Always fail validation for TDD tests
        // TODO Story 5.3: Replace with actual speaker count validation
        throw new WorkflowValidationException(
            "Insufficient speakers identified",
            Map.of("required", 6, "identified", 0, "placeholder", true)
        );
    }

    /**
     * Validates that all content has been submitted by speakers.
     *
     * Required for transition to QUALITY_REVIEW.
     *
     * NOTE: This is a placeholder implementation for Story 5.1a.
     * Full implementation depends on content submission tracking (Story 5.6).
     *
     * @param event Event being validated
     * @throws WorkflowValidationException if content not submitted
     */
    private void validateAllContentSubmitted(Event event) {
        // Placeholder: Always fail validation for TDD tests
        // TODO Story 5.6: Replace with actual content submission validation
        throw new WorkflowValidationException(
            "Not all content submitted",
            Map.of("submitted", 0, "required", 1, "placeholder", true)
        );
    }

    /**
     * Validates that minimum speaker threshold has been met.
     *
     * Required for transition to SLOT_ASSIGNMENT.
     *
     * Story 5.7 (BAT-11): Checks that we have at least one accepted speaker
     * before allowing slot assignment to begin.
     *
     * @param event Event being validated
     * @throws WorkflowValidationException if threshold not met
     */
    private void validateMinimumThresholdMet(Event event) {
        long acceptedSpeakers = speakerPoolRepository.countByEventIdAndStatus(
                event.getId(),
                ch.batbern.shared.types.SpeakerWorkflowState.ACCEPTED
        );

        // Check for speakers in later states as well (CONTENT_SUBMITTED, QUALITY_REVIEWED, CONFIRMED)
        long contentSubmitted = speakerPoolRepository.countByEventIdAndStatus(
                event.getId(),
                ch.batbern.shared.types.SpeakerWorkflowState.CONTENT_SUBMITTED
        );
        long qualityReviewed = speakerPoolRepository.countByEventIdAndStatus(
                event.getId(),
                ch.batbern.shared.types.SpeakerWorkflowState.QUALITY_REVIEWED
        );
        long confirmed = speakerPoolRepository.countByEventIdAndStatus(
                event.getId(),
                ch.batbern.shared.types.SpeakerWorkflowState.CONFIRMED
        );

        long totalReadyForSlots = acceptedSpeakers + contentSubmitted + qualityReviewed + confirmed;

        if (totalReadyForSlots < 1) {
            throw new WorkflowValidationException(
                    "Minimum threshold not met - need at least 1 accepted speaker for slot assignment",
                    Map.of(
                            "required", 1,
                            "accepted", acceptedSpeakers,
                            "totalReady", totalReadyForSlots
                    )
            );
        }

        log.debug("Threshold validation passed: {} speakers ready for slot assignment", totalReadyForSlots);
    }

    /**
     * Validates that all slots have been assigned.
     *
     * Required for transition to AGENDA_FINALIZED.
     *
     * Story 5.7 (BAT-11): Checks that all sessions have timing assigned
     * before finalizing the agenda.
     *
     * @param event Event being validated
     * @throws WorkflowValidationException if slots not assigned
     */
    private void validateAllSlotsAssigned(Event event) {
        long totalSessions = sessionRepository.countByEventId(event.getId());
        long sessionsWithTiming = sessionRepository.countByEventIdAndStartTimeNotNull(event.getId());

        if (totalSessions == 0) {
            throw new WorkflowValidationException(
                    "Cannot finalize agenda - no sessions exist for this event",
                    Map.of("totalSessions", 0)
            );
        }

        if (sessionsWithTiming < totalSessions) {
            throw new WorkflowValidationException(
                    "Cannot finalize agenda - not all sessions have timing assigned",
                    Map.of(
                            "totalSessions", totalSessions,
                            "sessionsWithTiming", sessionsWithTiming,
                            "unassigned", totalSessions - sessionsWithTiming
                    )
            );
        }

        log.debug("All {} sessions have timing assigned - agenda can be finalized", totalSessions);
    }

    /**
     * Validates that agenda is ready to be published.
     *
     * Required for transition to AGENDA_PUBLISHED.
     *
     * Story 5.7 (BAT-11): Checks that all sessions have timing assigned
     * before publishing the agenda to attendees.
     *
     * @param event Event being validated
     * @throws WorkflowValidationException if agenda not ready for publishing
     */
    private void validateQualityReviewComplete(Event event) {
        long totalSessions = sessionRepository.countByEventId(event.getId());
        long sessionsWithTiming = sessionRepository.countByEventIdAndStartTimeNotNull(event.getId());

        if (totalSessions == 0) {
            throw new WorkflowValidationException(
                    "Cannot publish agenda - no sessions exist for this event",
                    Map.of("totalSessions", 0)
            );
        }

        if (sessionsWithTiming < totalSessions) {
            throw new WorkflowValidationException(
                    "Cannot publish agenda - not all sessions have timing assigned",
                    Map.of(
                            "totalSessions", totalSessions,
                            "sessionsWithTiming", sessionsWithTiming,
                            "unassigned", totalSessions - sessionsWithTiming
                    )
            );
        }

        log.debug("All {} sessions have timing assigned - agenda ready for publishing", totalSessions);
    }
}
