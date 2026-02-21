package ch.batbern.events.service;

import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.SpeakerPool;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.repository.SessionRepository;
import ch.batbern.events.repository.SpeakerPoolRepository;
import ch.batbern.shared.events.DomainEventPublisher;
import ch.batbern.shared.events.SpeakerWorkflowStateChangeEvent;
import ch.batbern.shared.types.SpeakerWorkflowState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Speaker Workflow Service - manages speaker state transitions.
 * Story 5.3: Speaker Outreach Tracking
 * Architecture: Linear workflow with orthogonal slot assignment
 *
 * Handles speaker lifecycle state management with validation.
 * Ensures speakers can only transition through valid workflow states.
 *
 * State flow (linear):
 * IDENTIFIED → CONTACTED → READY → ACCEPTED → CONTENT_SUBMITTED → QUALITY_REVIEWED → CONFIRMED
 *
 * Slot assignment (orthogonal action):
 * - Sets session.startTime (not a state transition)
 * - Can happen at any point after ACCEPTED
 * - Auto-confirmation triggers when QUALITY_REVIEWED + slot assigned
 *
 * Three allowed flows:
 * 1. Quality first: ACCEPTED → CONTENT_SUBMITTED → QUALITY_REVIEWED → [assign slot] → CONFIRMED
 * 2. Slot first: ACCEPTED → [assign slot] → CONTENT_SUBMITTED → QUALITY_REVIEWED → CONFIRMED
 * 3. Slot during: ACCEPTED → CONTENT_SUBMITTED → [assign slot] → QUALITY_REVIEWED → CONFIRMED
 *
 * Alternative flows:
 * - Any state → DECLINED (speaker declines invitation)
 * - ACCEPTED → WITHDREW (speaker backs out after accepting)
 * - ACCEPTED → OVERFLOW (too many speakers)
 */
@Service
public class SpeakerWorkflowService {

    private static final Logger LOG = LoggerFactory.getLogger(SpeakerWorkflowService.class);

    private final SpeakerPoolRepository speakerPoolRepository;
    private final SessionRepository sessionRepository;
    private final EventRepository eventRepository;
    private final DomainEventPublisher eventPublisher;

    public SpeakerWorkflowService(
            SpeakerPoolRepository speakerPoolRepository,
            SessionRepository sessionRepository,
            EventRepository eventRepository,
            DomainEventPublisher eventPublisher
    ) {
        this.speakerPoolRepository = speakerPoolRepository;
        this.sessionRepository = sessionRepository;
        this.eventRepository = eventRepository;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Update speaker workflow state with validation.
     *
     * @param speakerId UUID of the speaker in the pool
     * @param newState New workflow state to transition to
     * @param organizerUsername Username of the organizer making the change
     * @throws IllegalArgumentException if speaker not found
     * @throws IllegalStateException if state transition is invalid
     */
    @Transactional
    public void updateSpeakerWorkflowState(
            UUID speakerId,
            SpeakerWorkflowState newState,
            String organizerUsername
    ) {
        SpeakerPool speaker = speakerPoolRepository.findById(speakerId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Speaker not found: " + speakerId
                ));

        SpeakerWorkflowState currentState = speaker.getStatus();

        // Validate state transition
        if (!isValidTransition(currentState, newState)) {
            throw new IllegalStateException(
                    String.format("Invalid state transition for speaker %s: %s → %s",
                            speakerId, currentState, newState)
            );
        }

        LOG.info("Transitioning speaker {} from {} to {} by organizer {}",
                speakerId, currentState, newState, organizerUsername);

        speaker.setStatus(newState);
        speakerPoolRepository.save(speaker);

        // Check for auto-confirmation when quality review completes
        if (newState == SpeakerWorkflowState.QUALITY_REVIEWED) {
            checkAndUpdateToConfirmed(speaker, organizerUsername);
        }

        // Publish SpeakerWorkflowStateChangeEvent to EventBridge (Story 6.0a CODE-001)
        publishStateChangeEvent(speaker, currentState, newState, organizerUsername);
    }

    /**
     * Publish a speaker workflow state change event.
     * Story 6.0a CODE-001: Domain event publishing
     *
     * @param speaker The speaker pool entry
     * @param fromState Previous workflow state
     * @param toState New workflow state
     * @param organizerUsername Username of the organizer making the change
     */
    private void publishStateChangeEvent(
            SpeakerPool speaker,
            SpeakerWorkflowState fromState,
            SpeakerWorkflowState toState,
            String organizerUsername
    ) {
        try {
            SpeakerWorkflowStateChangeEvent event = new SpeakerWorkflowStateChangeEvent(
                    speaker.getId(),
                    speaker.getEventId(),
                    fromState,
                    toState,
                    speaker.getUsername() != null ? speaker.getUsername() : organizerUsername
            );

            eventPublisher.publish(event);
            LOG.info("Published SpeakerWorkflowStateChangeEvent: {} -> {} for speaker {}",
                    fromState, toState, speaker.getId());
        } catch (Exception e) {
            // Log but don't fail the transaction if event publishing fails
            LOG.warn("Failed to publish SpeakerWorkflowStateChangeEvent for speaker {}: {}",
                    speaker.getId(), e.getMessage());
        }
    }

    /**
     * Validate if a state transition is allowed.
     *
     * Business rules (Linear Workflow with Orthogonal Slot Assignment):
     * - IDENTIFIED can transition to CONTACTED, DECLINED
     * - CONTACTED can transition to READY, DECLINED
     * - READY can transition to ACCEPTED, DECLINED
     * - ACCEPTED can transition to CONTENT_SUBMITTED, DECLINED, WITHDREW, OVERFLOW
     * - CONTENT_SUBMITTED can transition to QUALITY_REVIEWED, DECLINED, WITHDREW
     * - QUALITY_REVIEWED can transition to CONFIRMED, DECLINED, WITHDREW
     * - CONFIRMED auto-triggered when QUALITY_REVIEWED + slot assigned (session.startTime set)
     * - DECLINED and CONFIRMED are terminal states
     * - WITHDREW can transition back to ACCEPTED (if speaker wants back in)
     * - OVERFLOW can transition to ACCEPTED (if slot opens up)
     *
     * Note: SLOT_ASSIGNED state removed - slot assignment is an orthogonal action (sets session.startTime)
     *
     * @param currentState Current speaker state
     * @param newState Desired new state
     * @return true if transition is valid
     */
    private boolean isValidTransition(SpeakerWorkflowState currentState, SpeakerWorkflowState newState) {
        // Allow staying in same state (idempotent operations)
        if (currentState == newState) {
            return true;
        }

        return switch (currentState) {
            case IDENTIFIED -> newState == SpeakerWorkflowState.CONTACTED
                    || newState == SpeakerWorkflowState.DECLINED;

            case CONTACTED -> newState == SpeakerWorkflowState.READY
                    || newState == SpeakerWorkflowState.DECLINED;

            case READY -> newState == SpeakerWorkflowState.ACCEPTED
                    || newState == SpeakerWorkflowState.DECLINED;

            case ACCEPTED -> newState == SpeakerWorkflowState.CONTENT_SUBMITTED
                    || newState == SpeakerWorkflowState.DECLINED
                    || newState == SpeakerWorkflowState.WITHDREW
                    || newState == SpeakerWorkflowState.OVERFLOW;

            case CONTENT_SUBMITTED -> newState == SpeakerWorkflowState.QUALITY_REVIEWED
                    || newState == SpeakerWorkflowState.DECLINED
                    || newState == SpeakerWorkflowState.WITHDREW;

            case QUALITY_REVIEWED -> newState == SpeakerWorkflowState.CONFIRMED
                    || newState == SpeakerWorkflowState.DECLINED
                    || newState == SpeakerWorkflowState.WITHDREW;

            case WITHDREW -> newState == SpeakerWorkflowState.ACCEPTED; // Re-acceptance allowed

            case OVERFLOW -> newState == SpeakerWorkflowState.ACCEPTED; // Slot opened up

            // SLOT_ASSIGNED removed - slot assignment is now an action, not a state
            case SLOT_ASSIGNED -> false; // Should not be used

            case DECLINED, CONFIRMED -> false; // Terminal states

            default -> false;
        };
    }

    /**
     * Check if a speaker can be contacted (is in valid state for outreach).
     *
     * @param speakerId UUID of the speaker
     * @return true if speaker is in IDENTIFIED or OPEN state
     */
    public boolean canContactSpeaker(UUID speakerId) {
        return speakerPoolRepository.findById(speakerId)
                .map(speaker -> {
                    SpeakerWorkflowState state = speaker.getStatus();
                    return state == SpeakerWorkflowState.IDENTIFIED;
                })
                .orElse(false);
    }

    /**
     * Check if speaker has slot assigned and auto-update to CONFIRMED if so.
     *
     * This implements the simple linear workflow where:
     * - When speaker reaches QUALITY_REVIEWED state
     * - AND they have a time slot assigned (session.startTime != null)
     * - THEN auto-confirm them
     *
     * @param speaker The speaker to check
     * @param organizerUsername Username of the organizer (for audit trail)
     */
    private void checkAndUpdateToConfirmed(SpeakerPool speaker, String organizerUsername) {
        // Speaker just reached QUALITY_REVIEWED state
        // Check if they also have a slot assigned
        boolean hasSlotAssigned = hasTimeSlotAssigned(speaker);

        if (hasSlotAssigned) {
            LOG.info("Auto-confirming speaker {} - quality review complete and slot assigned",
                    speaker.getId());

            speaker.setStatus(SpeakerWorkflowState.CONFIRMED);
            speakerPoolRepository.save(speaker);

            LOG.info("Speaker {} auto-confirmed by system (triggered by organizer {})",
                    speaker.getId(), organizerUsername);

            // TODO: Publish SpeakerConfirmedEvent
        } else {
            LOG.debug("Speaker {} quality reviewed but no slot assigned yet - staying at QUALITY_REVIEWED",
                    speaker.getId());
        }
    }

    /**
     * Check if speaker has been assigned a time slot.
     * A slot is assigned if the speaker's session has a start_time set.
     *
     * @param speaker The speaker to check
     * @return true if time slot is assigned
     */
    private boolean hasTimeSlotAssigned(SpeakerPool speaker) {
        if (speaker.getSessionId() == null) {
            return false;
        }

        return sessionRepository.findById(speaker.getSessionId())
                .map(session -> session.getStartTime() != null)
                .orElse(false);
    }

    /**
     * Check if an event has speaker overflow (more accepted speakers than max slots).
     * Story 6.0a CODE-002: Overflow detection
     *
     * Counts speakers in ACCEPTED or higher states (CONTENT_SUBMITTED, QUALITY_REVIEWED, CONFIRMED)
     * and compares against the event's venue capacity.
     *
     * Note: Currently using venueCapacity as a proxy for max speaker slots.
     * In a future iteration, Event entity should have a dedicated maxSpeakerSlots field.
     *
     * @param eventId UUID of the event to check
     * @return true if accepted speaker count exceeds the event's max slots
     * @throws IllegalArgumentException if event not found
     */
    public boolean checkForOverflow(UUID eventId) {
        // Get event to determine max speaker slots
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new IllegalArgumentException("Event not found: " + eventId));

        // Count speakers in ACCEPTED or higher workflow states
        // These are speakers who have committed to speaking
        List<SpeakerWorkflowState> acceptedStates = List.of(
                SpeakerWorkflowState.ACCEPTED,
                SpeakerWorkflowState.CONTENT_SUBMITTED,
                SpeakerWorkflowState.QUALITY_REVIEWED,
                SpeakerWorkflowState.CONFIRMED
        );

        long acceptedCount = 0;
        for (SpeakerWorkflowState state : acceptedStates) {
            acceptedCount += speakerPoolRepository.countByEventIdAndStatus(eventId, state);
        }

        // Use venue capacity as a reasonable default for max speaker slots
        // A typical BATbern event has 6-8 speaker slots for ~200 attendees
        // Using venueCapacity / 25 as a heuristic (1 speaker per 25 attendees)
        int maxSpeakerSlots = Math.max(6, event.getVenueCapacity() / 25);

        boolean isOverflow = acceptedCount > maxSpeakerSlots;

        LOG.debug("Overflow check for event {}: accepted={}, maxSlots={}, overflow={}",
                eventId, acceptedCount, maxSpeakerSlots, isOverflow);

        return isOverflow;
    }

    /**
     * Get the current workflow state for a speaker.
     *
     * @param speakerId UUID of the speaker in the pool
     * @return Current workflow state
     * @throws IllegalArgumentException if speaker not found
     */
    public SpeakerWorkflowState getSpeakerWorkflowState(UUID speakerId) {
        return speakerPoolRepository.findById(speakerId)
                .map(SpeakerPool::getStatus)
                .orElseThrow(() -> new IllegalArgumentException("Speaker not found: " + speakerId));
    }

    /**
     * Get a speaker pool entry by ID.
     *
     * @param speakerId UUID of the speaker in the pool
     * @return SpeakerPool entry
     * @throws IllegalArgumentException if speaker not found
     */
    public SpeakerPool getSpeakerById(UUID speakerId) {
        return speakerPoolRepository.findById(speakerId)
                .orElseThrow(() -> new IllegalArgumentException("Speaker not found: " + speakerId));
    }
}
