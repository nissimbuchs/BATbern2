package ch.batbern.events.service;

import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.SpeakerPool;
import ch.batbern.events.dto.AddSpeakerToPoolRequest;
import ch.batbern.events.dto.SpeakerPoolResponse;
import ch.batbern.events.exception.EventNotFoundException;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.repository.SpeakerPoolRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Service for managing speaker pool during event brainstorming phase (Story 5.2 AC9-13).
 *
 * Handles business logic for adding potential speakers, assigning organizers for outreach,
 * and tracking speaker status.
 */
@Slf4j
@Service
public class SpeakerPoolService {

    private final SpeakerPoolRepository speakerPoolRepository;
    private final EventRepository eventRepository;

    public SpeakerPoolService(SpeakerPoolRepository speakerPoolRepository,
                              EventRepository eventRepository) {
        this.speakerPoolRepository = speakerPoolRepository;
        this.eventRepository = eventRepository;
    }

    /**
     * Add a speaker to the event speaker pool.
     *
     * @param eventCode the event code
     * @param request the speaker details
     * @return the created speaker pool entry
     * @throws EventNotFoundException if event not found
     * @throws IllegalArgumentException if speaker name is missing
     */
    @Transactional
    public SpeakerPoolResponse addSpeakerToPool(String eventCode, AddSpeakerToPoolRequest request) {
        // Validate event exists
        Event event = eventRepository.findByEventCode(eventCode)
                .orElseThrow(() -> new EventNotFoundException("Event not found: " + eventCode));

        // Validate speaker name is provided (AC9)
        if (request.getSpeakerName() == null || request.getSpeakerName().isBlank()) {
            throw new IllegalArgumentException("Speaker name is required");
        }

        // Create speaker pool entry (BUG FIX: Session will be created later when status = ACCEPTED)
        SpeakerPool speakerPool = new SpeakerPool();
        speakerPool.setEventId(event.getId());
        speakerPool.setSpeakerName(request.getSpeakerName());
        speakerPool.setCompany(request.getCompany());
        speakerPool.setExpertise(request.getExpertise());
        speakerPool.setAssignedOrganizerId(request.getAssignedOrganizerId());
        speakerPool.setNotes(request.getNotes());
        // AC13: Initial status = 'identified'
        speakerPool.setStatus(ch.batbern.shared.types.SpeakerWorkflowState.IDENTIFIED);
        // Session will be created when speaker accepts (status = ACCEPTED)
        speakerPool.setSessionId(null);

        // Persist speaker pool entry (AC18)
        SpeakerPool saved = speakerPoolRepository.save(speakerPool);
        log.info("Added speaker {} to pool (status: IDENTIFIED, no session yet)", saved.getSpeakerName());

        // TODO: Publish SpeakerAddedToPoolEvent (AC21) when domain events are implemented

        return SpeakerPoolResponse.fromEntity(saved);
    }

    /**
     * Get all speaker pool entries for an event.
     *
     * @param eventCode the event code
     * @return list of speaker pool entries
     */
    @Transactional(readOnly = true)
    public List<SpeakerPoolResponse> getSpeakerPoolForEvent(String eventCode) {
        Event event = eventRepository.findByEventCode(eventCode)
                .orElseThrow(() -> new IllegalArgumentException("Event not found: " + eventCode));

        return speakerPoolRepository.findByEventId(event.getId())
                .stream()
                .map(SpeakerPoolResponse::fromEntity)
                .collect(Collectors.toList());
    }

    /**
     * Assign a speaker pool entry to a specific organizer for outreach.
     *
     * @param speakerPoolId the speaker pool entry ID
     * @param organizerId the organizer username
     * @return the updated speaker pool entry
     */
    @Transactional
    public SpeakerPoolResponse assignSpeakerToOrganizer(String speakerPoolId, String organizerId) {
        SpeakerPool speakerPool = speakerPoolRepository.findById(java.util.UUID.fromString(speakerPoolId))
                .orElseThrow(() -> new IllegalArgumentException("Speaker pool entry not found: " + speakerPoolId));

        speakerPool.setAssignedOrganizerId(organizerId);
        SpeakerPool updated = speakerPoolRepository.save(speakerPool);

        return SpeakerPoolResponse.fromEntity(updated);
    }

    /**
     * Delete a speaker from the event speaker pool.
     *
     * @param eventCode the event code
     * @param speakerId the speaker pool entry ID
     * @throws EventNotFoundException if event not found
     * @throws IllegalArgumentException if speaker not found
     */
    @Transactional
    public void deleteSpeakerFromPool(String eventCode, String speakerId) {
        // Validate event exists
        Event event = eventRepository.findByEventCode(eventCode)
                .orElseThrow(() -> new EventNotFoundException("Event not found: " + eventCode));

        // Validate speaker exists and belongs to this event
        java.util.UUID speakerUuid = java.util.UUID.fromString(speakerId);
        SpeakerPool speakerPool = speakerPoolRepository.findById(speakerUuid)
                .orElseThrow(() -> new IllegalArgumentException("Speaker not found in pool: " + speakerId));

        // Verify speaker belongs to this event
        if (!speakerPool.getEventId().equals(event.getId())) {
            throw new IllegalArgumentException("Speaker does not belong to event: " + eventCode);
        }

        // Delete speaker from pool
        speakerPoolRepository.delete(speakerPool);
    }
}
