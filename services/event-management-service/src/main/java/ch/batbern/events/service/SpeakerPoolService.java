package ch.batbern.events.service;

import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.SpeakerPool;
import ch.batbern.events.dto.AddSpeakerToPoolRequest;
import ch.batbern.events.dto.SpeakerPoolResponse;
import ch.batbern.events.exception.EventNotFoundException;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.repository.SpeakerPoolRepository;
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

        // Create speaker pool entry
        SpeakerPool speakerPool = new SpeakerPool();
        speakerPool.setEventId(event.getId());
        speakerPool.setSpeakerName(request.getSpeakerName());
        speakerPool.setCompany(request.getCompany());
        speakerPool.setExpertise(request.getExpertise());
        speakerPool.setAssignedOrganizerId(request.getAssignedOrganizerId());
        speakerPool.setNotes(request.getNotes());
        speakerPool.setStatus("identified"); // AC13: Initial status = 'identified'

        // Persist speaker pool entry (AC18)
        SpeakerPool saved = speakerPoolRepository.save(speakerPool);

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
}
