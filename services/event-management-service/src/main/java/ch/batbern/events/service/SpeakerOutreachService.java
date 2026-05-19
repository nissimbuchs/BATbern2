package ch.batbern.events.service;

import ch.batbern.events.domain.OutreachHistory;
import ch.batbern.events.domain.SpeakerPool;
import ch.batbern.events.exception.SpeakerNotFoundException;
import ch.batbern.events.repository.OutreachHistoryRepository;
import ch.batbern.events.repository.SpeakerPoolRepository;
import ch.batbern.events.security.SecurityContextHelper;
import ch.batbern.events.service.workflow.SecurityPrincipal;
import ch.batbern.events.service.workflow.TransitionPayload;
import ch.batbern.shared.types.SpeakerWorkflowState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Speaker Outreach Service - business logic for tracking organizer contact attempts.
 * Story 5.3: Speaker Outreach Tracking
 *
 * Handles recording of outreach attempts and integrates with speaker workflow state machine.
 * When outreach is recorded, speaker state transitions from IDENTIFIED → CONTACTED.
 */
@Service
public class SpeakerOutreachService {

    private static final Logger LOG = LoggerFactory.getLogger(SpeakerOutreachService.class);

    private final OutreachHistoryRepository outreachHistoryRepository;
    private final SpeakerPoolRepository speakerPoolRepository;
    private final SpeakerWorkflowService speakerWorkflowService;
    private final SecurityContextHelper securityContextHelper;

    public SpeakerOutreachService(
            OutreachHistoryRepository outreachHistoryRepository,
            SpeakerPoolRepository speakerPoolRepository,
            SpeakerWorkflowService speakerWorkflowService,
            SecurityContextHelper securityContextHelper
    ) {
        this.outreachHistoryRepository = outreachHistoryRepository;
        this.speakerPoolRepository = speakerPoolRepository;
        this.securityContextHelper = securityContextHelper;
        this.speakerWorkflowService = speakerWorkflowService;
    }

    /**
     * Record a speaker outreach attempt.
     *
     * Business logic:
     * 1. Validate speaker exists and is in valid state (IDENTIFIED)
     * 2. Create outreach history record
     * 3. Transition speaker state to CONTACTED
     * 4. Publish SpeakerContactedEvent (future)
     *
     * @param speakerId UUID of the speaker being contacted
     * @param contactDate When the contact was made
     * @param contactMethod How contact was made (email, phone, in_person)
     * @param notes Free-text notes about the conversation
     * @param organizerUsername Username of organizer making contact
     * @return Created outreach history record
     * @throws SpeakerNotFoundException if speaker not found
     * @throws IllegalStateException if speaker not in valid state for contact
     */
    @Transactional
    public OutreachHistory recordOutreach(
            UUID speakerId,
            Instant contactDate,
            String contactMethod,
            String notes,
            String organizerUsername
    ) {
        LOG.info("Recording outreach for speaker {} by organizer {}", speakerId, organizerUsername);

        // 1. Validate speaker exists
        SpeakerPool speaker = speakerPoolRepository.findById(speakerId)
                .orElseThrow(() -> new SpeakerNotFoundException(speakerId));

        // 2. Validate speaker is in correct state for outreach
        // Only IDENTIFIED speakers should be contacted for the first time
        // But allow recording additional outreach attempts for already-contacted speakers
        SpeakerWorkflowState currentState = speaker.getStatus();
        if (currentState != SpeakerWorkflowState.IDENTIFIED
                && currentState != SpeakerWorkflowState.CONTACTED
                && currentState != SpeakerWorkflowState.READY) {
            throw new IllegalStateException(
                    String.format("Cannot record outreach for speaker %s in state %s. "
                                    + "Speaker must be in IDENTIFIED, CONTACTED, or READY state.",
                            speakerId, currentState)
            );
        }

        // 3. Create outreach history record
        OutreachHistory outreach = new OutreachHistory();
        outreach.setSpeakerPoolId(speakerId);
        outreach.setContactDate(contactDate);
        outreach.setContactMethod(validateContactMethod(contactMethod));
        outreach.setNotes(notes);
        outreach.setOrganizerUsername(organizerUsername);

        OutreachHistory savedOutreach = outreachHistoryRepository.save(outreach);

        LOG.info("Created outreach history record {} for speaker {}", savedOutreach.getId(), speakerId);

        // 4. Transition speaker state to CONTACTED (if not already) via the sole-writer.
        //    suppressHistoryRow=true: the OutreachHistory row above IS the audit entry; an
        //    additional status_history row would surface as a duplicate (and misleading)
        //    second timeline entry in the unified history feed.
        if (currentState == SpeakerWorkflowState.IDENTIFIED) {
            List<String> roles;
            try {
                roles = securityContextHelper.getCurrentUserRoles();
            } catch (SecurityException ex) {
                roles = List.of();
            }
            SecurityPrincipal actor = new SecurityPrincipal(organizerUsername, roles);
            TransitionPayload payload = TransitionPayload.builder()
                    .suppressHistoryRow(true)
                    .build();
            speakerWorkflowService.transition(
                    speakerId, SpeakerWorkflowState.CONTACTED, actor, payload);
            LOG.info("Transitioned speaker {} from IDENTIFIED to CONTACTED", speakerId);
        }

        // TODO: Publish SpeakerContactedEvent to EventBridge
        // Will be implemented when domain event publishing is added

        return savedOutreach;
    }

    /**
     * Get outreach history for a speaker, ordered by most recent first.
     *
     * @param speakerId UUID of the speaker
     * @return List of outreach attempts, most recent first
     */
    public List<OutreachHistory> getOutreachHistory(UUID speakerId) {
        return outreachHistoryRepository.findBySpeakerPoolIdOrderByContactDateDesc(speakerId);
    }

    /**
     * Validate contact method is one of the allowed values.
     *
     * @param contactMethod Method to validate
     * @return Validated and normalized contact method
     * @throws IllegalArgumentException if invalid method
     */
    private String validateContactMethod(String contactMethod) {
        if (contactMethod == null || contactMethod.isBlank()) {
            throw new IllegalArgumentException("Contact method is required");
        }

        String normalized = contactMethod.toLowerCase().trim();
        return switch (normalized) {
            case "email", "phone", "in_person", "in-person" -> normalized.replace("-", "_");
            default -> throw new IllegalArgumentException(
                    "Invalid contact method: " + contactMethod
                            + ". Must be one of: email, phone, in_person"
            );
        };
    }

    /**
     * Get count of outreach attempts for a speaker.
     *
     * @param speakerId UUID of the speaker
     * @return Number of outreach attempts
     */
    public long getOutreachAttemptCount(UUID speakerId) {
        return outreachHistoryRepository.findBySpeakerPoolId(speakerId).size();
    }

    /**
     * Check if a speaker has been contacted.
     *
     * @param speakerId UUID of the speaker
     * @return true if at least one outreach attempt exists
     */
    public boolean hasBeenContacted(UUID speakerId) {
        return !outreachHistoryRepository.findBySpeakerPoolId(speakerId).isEmpty();
    }
}
