package ch.batbern.events.service;

import ch.batbern.events.domain.Session;
import ch.batbern.events.domain.SessionUser;
import ch.batbern.events.dto.SessionResponse;
import ch.batbern.events.dto.TimetableSlot;
import ch.batbern.events.entity.EventTypeConfiguration;
import ch.batbern.events.exception.EventNotFoundException;
import ch.batbern.events.exception.StructuralSessionsAlreadyExistException;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.repository.EventTypeRepository;
import ch.batbern.events.repository.SessionRepository;
import ch.batbern.shared.exception.NotFoundException;
import ch.batbern.shared.service.SlugGenerationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

/**
 * Service for generating structural sessions (moderation, break, lunch) for an event.
 *
 * Structural sessions define the schedule skeleton — they are created with computed
 * start/end times derived from the event's EventTypeConfiguration. The organizer is
 * automatically assigned as MODERATOR on both moderation sessions.
 *
 * Timeline computation is delegated to {@link TimetableService#computeTimeline} to ensure
 * the generated structural sessions always align with what the slot-assignment UI displays.
 *
 * Entry point: POST /api/v1/events/{eventCode}/sessions/structural
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StructuralSessionService {

    static final List<String> STRUCTURAL_TYPES = List.of("moderation", "break", "lunch");

    private final EventRepository eventRepository;
    private final EventTypeRepository eventTypeRepository;
    private final SessionRepository sessionRepository;
    private final SessionService sessionService;
    private final SlugGenerationService slugGenerationService;
    private final TimetableService timetableService;

    /**
     * Generate structural sessions for an event.
     *
     * @param eventCode Public event code (e.g., "BATbern142")
     * @param overwrite If true, deletes existing structural sessions before generating
     * @return List of created SessionResponse DTOs
     * @throws EventNotFoundException                   if event is not found
     * @throws NotFoundException                        if event type config is not found
     * @throws StructuralSessionsAlreadyExistException if structural sessions exist and overwrite=false
     */
    @Transactional
    public List<SessionResponse> generateStructuralSessions(
            String eventCode,
            boolean overwrite) {

        // 1. Load event
        var event = eventRepository.findByEventCode(eventCode)
                .orElseThrow(() -> new EventNotFoundException("Event not found with code: " + eventCode));

        // 2. Load EventTypeConfiguration for this event's type
        if (event.getEventType() == null) {
            throw new NotFoundException("Event '" + eventCode + "' has no event type configured");
        }
        EventTypeConfiguration config = eventTypeRepository.findByType(event.getEventType())
                .orElseThrow(() -> new NotFoundException(
                        "Event type configuration not found for: " + event.getEventType()));

        // 3. Check for existing structural sessions
        List<Session> existing = sessionRepository.findByEventIdAndSessionTypeIn(
                event.getId(), STRUCTURAL_TYPES);
        if (!existing.isEmpty()) {
            if (!overwrite) {
                throw new StructuralSessionsAlreadyExistException(eventCode);
            }
            log.info("Overwriting {} existing structural sessions for event '{}'",
                    existing.size(), eventCode);
            sessionRepository.deleteByEventIdAndSessionTypeIn(event.getId(), STRUCTURAL_TYPES);
            sessionRepository.flush();
        }

        // 4. Delegate timeline computation to TimetableService
        LocalDate eventDate = LocalDate.ofInstant(event.getDate(), ZoneOffset.UTC);
        List<TimetableSlot> timeline = timetableService.computeTimeline(config, eventDate);

        // 5. Persist structural slots (skip SPEAKER_SLOT — those are implicit gaps)
        String organizerUsername = event.getOrganizerUsername();
        List<Session> createdSessions = new ArrayList<>();

        for (TimetableSlot slot : timeline) {
            if (slot.getType() == TimetableSlot.Type.SPEAKER_SLOT) {
                continue; // Speaker slots are not persisted — they are droppable gaps in the grid
            }
            Session session = buildSession(
                    event.getId(), eventCode,
                    slot.getTitle(), toSessionType(slot.getType()),
                    slot.getStartTime(), slot.getEndTime());
            if (slot.getType() == TimetableSlot.Type.MODERATION) {
                addModerator(session, organizerUsername);
            }
            sessionRepository.save(session);
            createdSessions.add(session);
        }

        log.info("Generated {} structural sessions for event '{}' (organizer={})",
                createdSessions.size(), eventCode, organizerUsername);

        // 6. Convert to response DTOs
        return createdSessions.stream()
                .map(s -> sessionService.toSessionResponse(s, eventCode))
                .toList();
    }

    // ─────────────────────────────────────────────────────────────────────────

    private Session buildSession(
            java.util.UUID eventId,
            String eventCode,
            String title,
            String sessionType,
            Instant startInstant,
            Instant endInstant) {

        String baseSlug = slugGenerationService.generateSessionSlug(title);
        String slug = slugGenerationService.ensureUniqueSlug(
                baseSlug, sessionRepository::existsBySessionSlug);

        return Session.builder()
                .sessionSlug(slug)
                .eventId(eventId)
                .eventCode(eventCode)
                .title(title)
                .sessionType(sessionType)
                .startTime(startInstant)
                .endTime(endInstant)
                .language("de")
                .build();
    }

    private void addModerator(Session session, String organizerUsername) {
        if (organizerUsername == null || organizerUsername.isBlank()) {
            return;
        }
        SessionUser moderator = SessionUser.builder()
                .session(session)
                .username(organizerUsername)
                .speakerRole(SessionUser.SpeakerRole.MODERATOR)
                .isConfirmed(true)
                .build();
        session.getSessionUsers().add(moderator);
    }

    private static String toSessionType(TimetableSlot.Type type) {
        return switch (type) {
            case MODERATION -> "moderation";
            case BREAK -> "break";
            case LUNCH -> "lunch";
            case SPEAKER_SLOT -> throw new IllegalArgumentException(
                    "SPEAKER_SLOT is not a persisted structural session type");
        };
    }
}
