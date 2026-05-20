package ch.batbern.events.service;

import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.Session;
import ch.batbern.events.domain.SessionContentVersion;
import ch.batbern.events.domain.SessionMaterial;
import ch.batbern.events.domain.SpeakerPool;
import ch.batbern.events.dto.AddSpeakerToPoolRequest;
import ch.batbern.events.dto.SpeakerPoolResponse;
import ch.batbern.events.exception.EventNotFoundException;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.repository.SessionContentHistoryRepository;
import ch.batbern.events.repository.SessionMaterialsRepository;
import ch.batbern.events.repository.SessionRepository;
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
    private final SessionContentHistoryRepository sessionContentHistoryRepository;
    private final SessionRepository sessionRepository;
    private final SessionMaterialsRepository sessionMaterialsRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final SecurityContextHelper securityContextHelper;

    public SpeakerPoolService(SpeakerPoolRepository speakerPoolRepository,
                              EventRepository eventRepository,
                              SessionContentHistoryRepository sessionContentHistoryRepository,
                              SessionRepository sessionRepository,
                              SessionMaterialsRepository sessionMaterialsRepository,
                              ApplicationEventPublisher eventPublisher,
                              SecurityContextHelper securityContextHelper) {
        this.speakerPoolRepository = speakerPoolRepository;
        this.eventRepository = eventRepository;
        this.sessionContentHistoryRepository = sessionContentHistoryRepository;
        this.sessionRepository = sessionRepository;
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

        // Story 11.E.8 consolidation: content history is now keyed by session_id. Batch-fetch
        // sessions first, then the latest version per session.
        List<UUID> sessionIds = speakers.stream()
                .map(SpeakerPool::getSessionId)
                .filter(id -> id != null)
                .distinct()
                .collect(Collectors.toList());
        Map<UUID, Session> sessionMap = sessionIds.isEmpty()
                ? Map.of()
                : sessionRepository.findAllById(sessionIds).stream()
                        .collect(Collectors.toMap(Session::getId, s -> s));

        // Build map of sessionId -> latest SessionContentVersion. Light N+1 inside the
        // stream is acceptable here (an event tops out at a few dozen speakers); if the
        // workload grows the SessionContentHistoryRepository can add a batch latest-per
        // -session query.
        Map<UUID, SessionContentVersion> latestVersionBySession = sessionIds.stream()
                .map(sid -> sessionContentHistoryRepository.findFirstBySessionIdOrderBySubmissionVersionDesc(sid))
                .filter(java.util.Optional::isPresent)
                .map(java.util.Optional::get)
                .collect(Collectors.toMap(
                        v -> v.getSession().getId(),
                        v -> v
                ));

        return speakers.stream()
                .map(speaker -> {
                    Session session = speaker.getSessionId() != null
                            ? sessionMap.get(speaker.getSessionId())
                            : null;
                    SessionContentVersion latestVersion = session != null
                            ? latestVersionBySession.get(session.getId())
                            : null;
                    SpeakerPoolResponse response = SpeakerPoolResponse.fromEntityWithContent(
                            speaker, session, latestVersion);
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
     * Partial update of a speaker pool entry. Only non-null fields are applied.
     *
     * @param eventCode the event code (validates ownership)
     * @param speakerId the speaker pool entry ID
     * @param request the patch request with optional fields
     * @return the updated speaker pool response
     */
    @Transactional
    public SpeakerPoolResponse patchEntry(String eventCode, String speakerId,
                                          ch.batbern.events.dto.PatchSpeakerPoolRequest request) {
        ch.batbern.events.domain.Event event = eventRepository.findByEventCode(eventCode)
                .orElseThrow(() -> new ch.batbern.events.exception.EventNotFoundException(
                        "Event not found: " + eventCode));

        SpeakerPool speakerPool = speakerPoolRepository.findById(java.util.UUID.fromString(speakerId))
                .orElseThrow(() -> new IllegalArgumentException("Speaker pool entry not found: " + speakerId));

        if (!speakerPool.getEventId().equals(event.getId())) {
            throw new IllegalArgumentException("Speaker does not belong to event: " + eventCode);
        }

        if (request.getSpeakerName() != null) {
            String trimmed = request.getSpeakerName().trim();
            if (trimmed.isEmpty()) {
                throw new IllegalArgumentException("speakerName must not be blank");
            }
            speakerPool.setSpeakerName(trimmed);
        }
        if (request.getCompany() != null) {
            speakerPool.setCompany(request.getCompany());
        }
        if (request.getExpertise() != null) {
            speakerPool.setExpertise(request.getExpertise());
        }
        if (request.getAssignedOrganizerId() != null) {
            speakerPool.setAssignedOrganizerId(request.getAssignedOrganizerId());
        }
        if (request.getNotes() != null) {
            speakerPool.setNotes(request.getNotes());
        }

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
