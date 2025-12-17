package ch.batbern.events.service;

import ch.batbern.events.domain.SpeakerPool;
import ch.batbern.events.repository.SpeakerPoolRepository;
import ch.batbern.shared.types.SpeakerWorkflowState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Speaker Workflow Service - manages speaker state transitions.
 * Story 5.3: Speaker Outreach Tracking
 *
 * Handles speaker lifecycle state management with validation.
 * Ensures speakers can only transition through valid workflow states.
 *
 * State flow:
 * IDENTIFIED → CONTACTED → READY → ACCEPTED → CONTENT_SUBMITTED → QUALITY_REVIEWED → SLOT_ASSIGNED → CONFIRMED
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

    public SpeakerWorkflowService(SpeakerPoolRepository speakerPoolRepository) {
        this.speakerPoolRepository = speakerPoolRepository;
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

        // TODO: Publish SpeakerWorkflowStateChangeEvent to EventBridge
        // Will be implemented when domain event publishing is added
    }

    /**
     * Validate if a state transition is allowed.
     *
     * Business rules:
     * - IDENTIFIED can transition to CONTACTED, DECLINED
     * - CONTACTED can transition to READY, DECLINED
     * - READY can transition to ACCEPTED, DECLINED
     * - ACCEPTED can transition to CONTENT_SUBMITTED, DECLINED, WITHDREW, OVERFLOW
     * - CONTENT_SUBMITTED can transition to QUALITY_REVIEWED, DECLINED, WITHDREW
     * - QUALITY_REVIEWED can transition to SLOT_ASSIGNED, DECLINED, WITHDREW
     * - SLOT_ASSIGNED can transition to CONFIRMED, WITHDREW
     * - DECLINED and CONFIRMED are terminal states
     * - WITHDREW can transition back to ACCEPTED (if speaker wants back in)
     * - OVERFLOW can transition to ACCEPTED (if slot opens up)
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

            case QUALITY_REVIEWED -> newState == SpeakerWorkflowState.SLOT_ASSIGNED
                    || newState == SpeakerWorkflowState.DECLINED
                    || newState == SpeakerWorkflowState.WITHDREW;

            case SLOT_ASSIGNED -> newState == SpeakerWorkflowState.CONFIRMED
                    || newState == SpeakerWorkflowState.WITHDREW;

            case WITHDREW -> newState == SpeakerWorkflowState.ACCEPTED; // Re-acceptance allowed

            case OVERFLOW -> newState == SpeakerWorkflowState.ACCEPTED; // Slot opened up

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
}
