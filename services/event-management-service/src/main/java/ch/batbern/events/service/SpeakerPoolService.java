package ch.batbern.events.service;

import ch.batbern.events.domain.ContentSubmission;
import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.SessionMaterial;
import ch.batbern.events.domain.SpeakerPool;
import ch.batbern.events.dto.AddSpeakerToPoolRequest;
import ch.batbern.events.dto.SpeakerPoolResponse;
import ch.batbern.events.exception.EventNotFoundException;
import ch.batbern.events.repository.ContentSubmissionRepository;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.repository.SessionMaterialsRepository;
import ch.batbern.events.repository.SpeakerPoolRepository;
import ch.batbern.events.security.SecurityContextHelper;
import ch.batbern.shared.events.SpeakerAddedToPoolEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
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
    private final ContentSubmissionRepository contentSubmissionRepository;
    private final SessionMaterialsRepository sessionMaterialsRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final SecurityContextHelper securityContextHelper;

    public SpeakerPoolService(SpeakerPoolRepository speakerPoolRepository,
                              EventRepository eventRepository,
                              ContentSubmissionRepository contentSubmissionRepository,
                              SessionMaterialsRepository sessionMaterialsRepository,
                              ApplicationEventPublisher eventPublisher,
                              SecurityContextHelper securityContextHelper) {
        this.speakerPoolRepository = speakerPoolRepository;
        this.eventRepository = eventRepository;
        this.contentSubmissionRepository = contentSubmissionRepository;
        this.sessionMaterialsRepository = sessionMaterialsRepository;
        this.eventPublisher = eventPublisher;
        this.securityContextHelper = securityContextHelper;
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
     * Get all speaker pool entries for an event with content submission data.
     *
     * Story 6.3: Include submitted title and abstract for organizer dashboard.
     *
     * @param eventCode the event code
     * @return list of speaker pool entries with content submission data
     */
    @Transactional(readOnly = true)
    public List<SpeakerPoolResponse> getSpeakerPoolForEvent(String eventCode) {
        Event event = eventRepository.findByEventCode(eventCode)
                .orElseThrow(() -> new IllegalArgumentException("Event not found: " + eventCode));

        List<SpeakerPool> speakers = speakerPoolRepository.findByEventId(event.getId());

        // Fetch latest content submissions for all speakers in one query
        // This avoids N+1 query problem
        List<UUID> speakerIds = speakers.stream()
                .map(SpeakerPool::getId)
                .collect(Collectors.toList());

        // Build map of speakerId -> latest content submission
        Map<UUID, ContentSubmission> contentMap = speakerIds.stream()
                .map(id -> contentSubmissionRepository.findFirstBySpeakerPoolIdOrderBySubmissionVersionDesc(id))
                .filter(opt -> opt.isPresent())
                .map(opt -> opt.get())
                .collect(Collectors.toMap(
                        cs -> cs.getSpeakerPool().getId(),
                        cs -> cs
                ));

        return speakers.stream()
                .map(speaker -> {
                    ContentSubmission content = contentMap.get(speaker.getId());
                    SpeakerPoolResponse response;
                    if (content != null) {
                        response = SpeakerPoolResponse.fromEntityWithContent(
                                speaker,
                                content.getTitle(),
                                content.getContentAbstract()
                        );
                    } else {
                        response = SpeakerPoolResponse.fromEntity(speaker);
                    }
                    // Enrich with material info if session exists
                    if (speaker.getSessionId() != null) {
                        List<SessionMaterial> materials = sessionMaterialsRepository
                                .findBySession_IdOrderByCreatedAtAsc(speaker.getSessionId());
                        if (!materials.isEmpty()) {
                            SessionMaterial latest = materials.get(materials.size() - 1);
                            response.setMaterialFileName(latest.getFileName());
                            response.setMaterialCloudFrontUrl(latest.getCloudFrontUrl());
                        }
                    }
                    return response;
                })
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
