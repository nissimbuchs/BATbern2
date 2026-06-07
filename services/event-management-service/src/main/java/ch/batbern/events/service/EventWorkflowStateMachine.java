package ch.batbern.events.service;

import ch.batbern.events.domain.Event;
import ch.batbern.events.exception.WorkflowValidationException;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.shared.events.DomainEventPublisher;
import ch.batbern.shared.events.EventWorkflowTransitionEvent;
import ch.batbern.shared.types.EventWorkflowState;
import ch.batbern.shared.types.SpeakerWorkflowState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
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
    private final EventArchivalCleanupService eventArchivalCleanupService;

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

        // Run archival cleanup when transitioning to ARCHIVED (Story 10.18)
        if (targetState == EventWorkflowState.ARCHIVED) {
            eventArchivalCleanupService.cleanup(savedEvent.getId(), eventCode);
        }

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
            // 8-State Model: AGENDA_FINALIZED removed (V82)
            case SLOT_ASSIGNMENT:
                validateMinimumThresholdMet(event);
                break;
            case AGENDA_PUBLISHED:
                // Story 11.B.3: Renamed from validateQualityReviewComplete and reanchored
                // on the derived is_publishable predicate (ADR-009 §0.1). Replaces the
                // dual session-timing + content-status check with a single JOIN-backed
                // count.
                validateAllSpeakersConfirmed(event);
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

        // Check for speakers in later content-lifecycle states (ADR-009 §0.1: CONFIRMED is
        // gone; is_publishable is derived at read time, exposure lands in 11.B.3).
        long contentSubmitted = speakerPoolRepository.countByEventIdAndStatus(
                event.getId(),
                ch.batbern.shared.types.SpeakerWorkflowState.CONTENT_SUBMITTED
        );
        long qualityReviewed = speakerPoolRepository.countByEventIdAndStatus(
                event.getId(),
                ch.batbern.shared.types.SpeakerWorkflowState.QUALITY_REVIEWED
        );

        long totalReadyForSlots = acceptedSpeakers + contentSubmitted + qualityReviewed;

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
     * Validates that every accepted-or-beyond speaker is publishable.
     *
     * <p>Required for transition to {@code AGENDA_PUBLISHED}.
     *
     * <p>Story 11.B.3 (ADR-009 §0.1): the {@code is_publishable} predicate is
     * {@code QUALITY_REVIEWED AND session.start_time IS NOT NULL}. The gate fires when
     * the count of "accepted or beyond" speakers (ACCEPTED + CONTENT_SUBMITTED +
     * QUALITY_REVIEWED) exceeds the count of publishable speakers — i.e., at least one
     * speaker has accepted but is missing either quality review or session timing.
     *
     * <p>Replaces the prior {@code validateQualityReviewComplete} which layered two
     * predicates ("all sessions have timing" + implicit quality state). The new gate
     * folds both into the single JOIN-backed {@code countPublishableByEventId} query.
     *
     * @param event Event being validated
     * @throws WorkflowValidationException if any accepted speaker is not publishable
     */
    private void validateAllSpeakersConfirmed(Event event) {
        long acceptedOrBeyondSpeakers = speakerPoolRepository.countByEventIdAndStatusIn(
                event.getId(),
                List.of(
                        SpeakerWorkflowState.ACCEPTED,
                        SpeakerWorkflowState.CONTENT_SUBMITTED,
                        SpeakerWorkflowState.QUALITY_REVIEWED
                )
        );

        if (acceptedOrBeyondSpeakers == 0) {
            throw new WorkflowValidationException(
                    "Cannot publish agenda — no accepted speakers exist for this event",
                    Map.of("acceptedOrBeyond", 0)
            );
        }

        long publishableSpeakers = speakerPoolRepository.countPublishableByEventId(event.getId());

        if (acceptedOrBeyondSpeakers > publishableSpeakers) {
            throw new WorkflowValidationException(
                    "Not all accepted speakers are publishable",
                    Map.of(
                            "accepted", acceptedOrBeyondSpeakers,
                            "publishable", publishableSpeakers,
                            "gap", acceptedOrBeyondSpeakers - publishableSpeakers
                    )
            );
        }

        log.debug("Publishable check passed: {}/{} accepted speakers are publishable",
                publishableSpeakers, acceptedOrBeyondSpeakers);
    }
}
