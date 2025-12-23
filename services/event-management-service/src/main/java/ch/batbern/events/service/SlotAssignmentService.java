package ch.batbern.events.service;

import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.Session;
import ch.batbern.events.domain.SpeakerPool;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.repository.SessionRepository;
import ch.batbern.events.repository.SpeakerPoolRepository;
import ch.batbern.shared.exception.NotFoundException;
import ch.batbern.shared.types.SpeakerWorkflowState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Slot Assignment Service - manages speaker-to-slot assignments.
 * Architecture: 06a-workflow-state-machines.md (simplified linear model)
 *
 * Handles:
 * - Assigning speakers to specific time slots (sets session.startTime)
 * - Automatic slot assignment using preferences/algorithms
 * - Manual slot assignment by organizers
 *
 * Note: Slot assignment is an orthogonal action (not a state transition)
 * - Does NOT change speaker workflow state
 * - Just sets session.startTime to assign the slot
 * - Auto-confirmation triggers when speaker reaches QUALITY_REVIEWED + has slot
 */
@Service
@Slf4j
@RequiredArgsConstructor
@Transactional
public class SlotAssignmentService {

    private final SessionRepository sessionRepository;
    private final SpeakerPoolRepository speakerPoolRepository;
    private final EventRepository eventRepository;
    private final SpeakerWorkflowService speakerWorkflowService;

    /**
     * Assign a speaker to a specific time slot (manual assignment).
     *
     * This method:
     * 1. Validates the session and speaker exist
     * 2. Validates the session has a time slot (start_time is set)
     * 3. Validates the speaker is in an acceptable state for slot assignment
     * 4. Links the speaker to the session (sets speaker.sessionId)
     *
     * Note: This does NOT change the speaker's workflow state.
     * Auto-confirmation will trigger when speaker reaches QUALITY_REVIEWED state
     * and detects they already have a slot assigned.
     *
     * @param eventCode Event code
     * @param speakerId Speaker pool ID
     * @param sessionId Session ID with time slot
     * @param organizerUsername Organizer making the assignment
     * @throws NotFoundException if event, speaker, or session not found
     * @throws IllegalStateException if session has no time slot or speaker not in valid state
     */
    public void assignSpeakerToSlot(
            String eventCode,
            UUID speakerId,
            UUID sessionId,
            String organizerUsername
    ) {
        // Validate event exists
        Event event = eventRepository.findByEventCode(eventCode)
                .orElseThrow(() -> new NotFoundException("Event not found: " + eventCode));

        // Validate speaker exists and belongs to this event
        SpeakerPool speaker = speakerPoolRepository.findById(speakerId)
                .orElseThrow(() -> new NotFoundException("Speaker not found: " + speakerId));

        if (!speaker.getEventId().equals(event.getId())) {
            throw new IllegalArgumentException(
                    String.format("Speaker %s does not belong to event %s", speakerId, eventCode));
        }

        // Validate session exists and has a time slot
        Session session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new NotFoundException("Session not found: " + sessionId));

        if (session.getStartTime() == null) {
            throw new IllegalStateException(
                    String.format("Session %s has no time slot assigned (start_time is null)", sessionId));
        }

        if (!session.getEventId().equals(event.getId())) {
            throw new IllegalArgumentException(
                    String.format("Session %s does not belong to event %s", sessionId, eventCode));
        }

        // Validate speaker is in acceptable state for slot assignment
        SpeakerWorkflowState currentState = speaker.getStatus();
        if (!isValidForSlotAssignment(currentState)) {
            throw new IllegalStateException(
                    String.format("Speaker %s in state %s cannot be assigned to slot "
                            + "(must be ACCEPTED, CONTENT_SUBMITTED, or QUALITY_REVIEWED)",
                            speakerId, currentState));
        }

        // Assign speaker to session (orthogonal action - does NOT change workflow state)
        speaker.setSessionId(sessionId);
        speakerPoolRepository.save(speaker);

        log.info("Assigned speaker {} to session {} (slot: {}) for event {} by organizer {} "
                        + "- speaker remains in state {}",
                speakerId, sessionId, session.getStartTime(), eventCode, organizerUsername, currentState);

        // Note: If speaker is already in QUALITY_REVIEWED state, they should be manually
        // transitioned to CONFIRMED by calling speakerWorkflowService.updateSpeakerWorkflowState()
        // since we just assigned the final missing piece (the slot)
    }

    /**
     * Unassign a speaker from their current slot.
     *
     * Note: This only clears the session assignment (speaker.sessionId = null).
     * It does NOT change the speaker's workflow state.
     * If speaker was CONFIRMED, they should be manually moved back to QUALITY_REVIEWED.
     *
     * @param eventCode Event code
     * @param speakerId Speaker pool ID
     * @param organizerUsername Organizer making the change
     * @throws NotFoundException if speaker not found
     */
    public void unassignSpeakerFromSlot(
            String eventCode,
            UUID speakerId,
            String organizerUsername
    ) {
        SpeakerPool speaker = speakerPoolRepository.findById(speakerId)
                .orElseThrow(() -> new NotFoundException("Speaker not found: " + speakerId));

        UUID previousSessionId = speaker.getSessionId();
        speaker.setSessionId(null);
        speakerPoolRepository.save(speaker);

        log.info("Unassigned speaker {} from session {} for event {} by organizer {} - speaker remains in state {}",
                speakerId, previousSessionId, eventCode, organizerUsername, speaker.getStatus());

        // Note: If speaker was CONFIRMED, organizer should manually revert them to QUALITY_REVIEWED
        // since they no longer meet confirmation criteria (missing slot assignment)
    }

    /**
     * Get all speakers assigned to slots for an event.
     *
     * @param eventCode Event code
     * @return List of speakers with slot assignments
     */
    @Transactional(readOnly = true)
    public List<SpeakerPool> getAssignedSpeakers(String eventCode) {
        Event event = eventRepository.findByEventCode(eventCode)
                .orElseThrow(() -> new NotFoundException("Event not found: " + eventCode));

        return speakerPoolRepository.findByEventId(event.getId()).stream()
                .filter(speaker -> speaker.getSessionId() != null)
                .filter(speaker -> hasTimeSlot(speaker.getSessionId()))
                .toList();
    }

    /**
     * Get all unassigned accepted speakers for an event.
     *
     * @param eventCode Event code
     * @return List of accepted speakers without slot assignments
     */
    @Transactional(readOnly = true)
    public List<SpeakerPool> getUnassignedAcceptedSpeakers(String eventCode) {
        Event event = eventRepository.findByEventCode(eventCode)
                .orElseThrow(() -> new NotFoundException("Event not found: " + eventCode));

        return speakerPoolRepository.findByEventId(event.getId()).stream()
                .filter(speaker -> speaker.getStatus() == SpeakerWorkflowState.ACCEPTED
                        || speaker.getStatus() == SpeakerWorkflowState.CONTENT_SUBMITTED
                        || speaker.getStatus() == SpeakerWorkflowState.QUALITY_REVIEWED)
                .filter(speaker -> speaker.getSessionId() == null || !hasTimeSlot(speaker.getSessionId()))
                .toList();
    }

    /**
     * Check if a speaker state is valid for slot assignment.
     *
     * Slot can be assigned when speaker is:
     * - ACCEPTED (early slot assignment before content submission)
     * - CONTENT_SUBMITTED (slot assigned during review process)
     * - QUALITY_REVIEWED (slot assigned after review complete)
     * - CONFIRMED (re-assigning to different slot)
     *
     * @param state Current speaker workflow state
     * @return true if speaker can be assigned to a slot
     */
    private boolean isValidForSlotAssignment(SpeakerWorkflowState state) {
        return state == SpeakerWorkflowState.ACCEPTED
                || state == SpeakerWorkflowState.CONTENT_SUBMITTED
                || state == SpeakerWorkflowState.QUALITY_REVIEWED
                || state == SpeakerWorkflowState.CONFIRMED;
    }

    /**
     * Check if a session has a time slot assigned.
     *
     * @param sessionId Session ID
     * @return true if session has start_time set
     */
    private boolean hasTimeSlot(UUID sessionId) {
        return sessionRepository.findById(sessionId)
                .map(session -> session.getStartTime() != null)
                .orElse(false);
    }
}
