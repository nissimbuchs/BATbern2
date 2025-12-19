package ch.batbern.events.service;

import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.Session;
import ch.batbern.events.domain.SpeakerPool;
import ch.batbern.events.dto.AddSpeakerToPoolRequest;
import ch.batbern.events.dto.SpeakerPoolResponse;
import ch.batbern.events.exception.EventNotFoundException;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.repository.SessionRepository;
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
    private final SessionRepository sessionRepository;

    public SpeakerPoolService(SpeakerPoolRepository speakerPoolRepository,
                              EventRepository eventRepository,
                              SessionRepository sessionRepository) {
        this.speakerPoolRepository = speakerPoolRepository;
        this.eventRepository = eventRepository;
        this.sessionRepository = sessionRepository;
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

        // Story 5.4 IMPL-001: Create empty session placeholder for this speaker
        // This session will be updated with description and timing as planning progresses
        Session session = new Session();
        session.setEventId(event.getId());
        session.setSessionSlug(generateSessionSlug(event.getEventCode(), request.getSpeakerName()));

        // Set title to "SpeakerName - Company" format for identification
        String sessionTitle = request.getSpeakerName();
        if (request.getCompany() != null && !request.getCompany().isBlank()) {
            sessionTitle += " - " + request.getCompany();
        }
        session.setTitle(sessionTitle);
        // Leave description, timing, and other fields null - they will be filled during planning

        Session savedSession = sessionRepository.save(session);
        log.info("Created empty session {} for speaker {}", savedSession.getSessionSlug(), request.getSpeakerName());

        // Create speaker pool entry linked to session
        SpeakerPool speakerPool = new SpeakerPool();
        speakerPool.setEventId(event.getId());
        speakerPool.setSpeakerName(request.getSpeakerName());
        speakerPool.setCompany(request.getCompany());
        speakerPool.setExpertise(request.getExpertise());
        speakerPool.setAssignedOrganizerId(request.getAssignedOrganizerId());
        speakerPool.setNotes(request.getNotes());
        // AC13: Initial status = 'identified'
        speakerPool.setStatus(ch.batbern.shared.types.SpeakerWorkflowState.IDENTIFIED);
        // Story 5.4 IMPL-001: Link to created session
        speakerPool.setSessionId(savedSession.getId());

        // Persist speaker pool entry (AC18)
        SpeakerPool saved = speakerPoolRepository.save(speakerPool);
        log.info("Added speaker {} to pool with session link {}", saved.getSpeakerName(), savedSession.getId());

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

    /**
     * Generate a URL-friendly session slug from event code and speaker name.
     * Story 5.4 IMPL-001: Creates unique session slugs for placeholder sessions
     *
     * Format: {eventCode}-speaker-{cleanName}-{timestamp}
     * Example: bat2025-speaker-alice-johnson-12345
     *
     * @param eventCode Event code
     * @param speakerName Speaker name
     * @return URL-friendly slug
     */
    private String generateSessionSlug(String eventCode, String speakerName) {
        String cleanName = speakerName.toLowerCase()
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-|-$", "");
        String timestamp = String.valueOf(System.currentTimeMillis()).substring(8);
        return String.format("%s-speaker-%s-%s", eventCode.toLowerCase(), cleanName, timestamp);
    }
}
