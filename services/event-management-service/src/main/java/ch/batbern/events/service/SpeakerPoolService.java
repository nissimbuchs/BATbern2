package ch.batbern.events.service;

import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.Speaker;
import ch.batbern.events.domain.SpeakerPool;
import ch.batbern.events.dto.AddSpeakerToPoolRequest;
import ch.batbern.events.dto.SpeakerPoolResponse;
import ch.batbern.events.dto.UpdateSpeakerPoolRequest;
import ch.batbern.events.exception.EventNotFoundException;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.repository.SpeakerPoolRepository;
import ch.batbern.events.security.SecurityContextHelper;
import ch.batbern.shared.events.SpeakerAddedToPoolEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
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
    private final ApplicationEventPublisher eventPublisher;
    private final SecurityContextHelper securityContextHelper;
    private final SpeakerService speakerService;

    public SpeakerPoolService(SpeakerPoolRepository speakerPoolRepository,
                              EventRepository eventRepository,
                              ApplicationEventPublisher eventPublisher,
                              SecurityContextHelper securityContextHelper,
                              SpeakerService speakerService) {
        this.speakerPoolRepository = speakerPoolRepository;
        this.eventRepository = eventRepository;
        this.eventPublisher = eventPublisher;
        this.securityContextHelper = securityContextHelper;
        this.speakerService = speakerService;
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
        speakerPool.setEmail(request.getEmail());
        speakerPool.setPhone(request.getPhone());
        // AC13: Initial status = 'identified'
        speakerPool.setStatus(ch.batbern.shared.types.SpeakerWorkflowState.IDENTIFIED);
        // Session will be created when speaker accepts (status = ACCEPTED)
        speakerPool.setSessionId(null);

        // Persist speaker pool entry (AC18)
        SpeakerPool saved = speakerPoolRepository.save(speakerPool);
        log.info("Added speaker {} to pool (status: IDENTIFIED, no session yet)", saved.getSpeakerName());

        // Publish SpeakerAddedToPoolEvent to trigger workflow transition (AC21)
        String addedBy = securityContextHelper.getCurrentUsername();
        SpeakerAddedToPoolEvent speakerAddedEvent = SpeakerAddedToPoolEvent.builder()
                .eventId(event.getId())
                .eventCode(eventCode)
                .speakerPoolId(saved.getId())
                .speakerName(saved.getSpeakerName())
                .company(saved.getCompany())
                .expertise(saved.getExpertise())
                .assignedOrganizerId(saved.getAssignedOrganizerId())
                .addedBy(addedBy)
                .build();
        eventPublisher.publishEvent(speakerAddedEvent);
        log.debug("Published SpeakerAddedToPoolEvent for speaker: {}, event: {}", saved.getSpeakerName(), eventCode);

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
     * Update a speaker in the event speaker pool.
     *
     * @param eventCode the event code
     * @param speakerId the speaker pool entry ID
     * @param request the updated speaker details
     * @return the updated speaker pool entry
     * @throws EventNotFoundException if event not found
     * @throws IllegalArgumentException if speaker not found or doesn't belong to event
     */
    @Transactional
    public SpeakerPoolResponse updateSpeakerInPool(
            String eventCode,
            String speakerId,
            UpdateSpeakerPoolRequest request) {
        // Validate event exists
        Event event = eventRepository.findByEventCode(eventCode)
                .orElseThrow(() -> new EventNotFoundException("Event not found: " + eventCode));

        // Validate speaker exists
        java.util.UUID speakerUuid = java.util.UUID.fromString(speakerId);
        SpeakerPool speakerPool = speakerPoolRepository.findById(speakerUuid)
                .orElseThrow(() -> new IllegalArgumentException("Speaker not found in pool: " + speakerId));

        // Verify speaker belongs to this event
        if (!speakerPool.getEventId().equals(event.getId())) {
            throw new IllegalArgumentException("Speaker does not belong to event: " + eventCode);
        }

        // Update editable fields only (status and sessionId are managed by workflow)
        speakerPool.setSpeakerName(request.getSpeakerName());
        speakerPool.setCompany(request.getCompany());
        speakerPool.setExpertise(request.getExpertise());
        speakerPool.setAssignedOrganizerId(request.getAssignedOrganizerId());
        speakerPool.setNotes(request.getNotes());
        speakerPool.setEmail(request.getEmail());
        speakerPool.setPhone(request.getPhone());

        // Persist changes
        SpeakerPool updated = speakerPoolRepository.save(speakerPool);
        log.info("Updated speaker {} in pool for event {}", updated.getSpeakerName(), eventCode);

        return SpeakerPoolResponse.fromEntity(updated);
    }

    /**
     * Manually link a speaker pool entry to a user account.
     * Story 6.3: Speaker Account Linking - Manual Fallback for Organizers
     *
     * <p>This method allows organizers to manually link a speaker pool entry
     * to a registered user account when automatic linking (via email match
     * during registration) was not possible (e.g., different email addresses).
     *
     * <p>The operation is idempotent - if already linked to the same username, no change occurs.
     *
     * @param eventCode the event code
     * @param speakerPoolId the speaker pool entry ID
     * @param username the username to link to (ADR-003 identifier)
     * @return the updated speaker pool entry
     * @throws EventNotFoundException if event not found
     * @throws IllegalArgumentException if speaker not found, doesn't belong to event,
     *         or is already linked to a different user
     */
    @Transactional
    public SpeakerPoolResponse linkToUser(String eventCode, String speakerPoolId, String username) {
        // Validate event exists
        Event event = eventRepository.findByEventCode(eventCode)
                .orElseThrow(() -> new EventNotFoundException("Event not found: " + eventCode));

        // Validate speaker pool entry exists
        UUID speakerUuid = UUID.fromString(speakerPoolId);
        SpeakerPool speakerPool = speakerPoolRepository.findById(speakerUuid)
                .orElseThrow(() -> new IllegalArgumentException("Speaker not found in pool: " + speakerPoolId));

        // Verify speaker belongs to this event
        if (!speakerPool.getEventId().equals(event.getId())) {
            throw new IllegalArgumentException("Speaker does not belong to event: " + eventCode);
        }

        // Check if already linked to a different user
        if (speakerPool.getUsername() != null && !speakerPool.getUsername().equals(username)) {
            throw new IllegalArgumentException(
                    "Speaker pool entry is already linked to user: " + speakerPool.getUsername());
        }

        // Idempotent: if already linked to same user, return without changes
        if (username.equals(speakerPool.getUsername())) {
            log.info("Speaker pool entry {} already linked to user {}, no action needed",
                    speakerPoolId, username);
            return SpeakerPoolResponse.fromEntity(speakerPool);
        }

        // Link the speaker pool entry to the user
        speakerPool.setUsername(username);
        SpeakerPool updated = speakerPoolRepository.save(speakerPool);
        log.info("Manually linked speaker pool entry {} to user {} for event {}",
                speakerPoolId, username, eventCode);

        // Ensure Speaker entity exists for this user (Story 6.3: AC4)
        Speaker speaker = speakerService.ensureSpeakerExists(username);
        log.info("Ensured Speaker entity exists for user {} (Speaker ID: {})",
                username, speaker.getId());

        return SpeakerPoolResponse.fromEntity(updated);
    }
}
