package ch.batbern.events.service;

import ch.batbern.events.domain.Session;
import ch.batbern.events.domain.SessionUser;
import ch.batbern.events.dto.SessionResponse;
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
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Service for generating structural sessions (moderation, break, lunch) for an event.
 *
 * Structural sessions define the schedule skeleton — they are created with computed
 * start/end times derived from the event's EventTypeConfiguration. The organizer is
 * automatically assigned as MODERATOR on both moderation sessions.
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

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

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

        // 4. Compute timeline anchor from event date + typicalStartTime
        LocalDate eventDate = LocalDate.ofInstant(event.getDate(), ZoneOffset.UTC);
        LocalTime startTime = config.getTypicalStartTime() != null
                ? config.getTypicalStartTime()
                : LocalTime.of(9, 0);

        // 5. Generate sessions
        String organizerUsername = event.getOrganizerUsername();
        List<Session> createdSessions = new ArrayList<>();
        LocalDateTime cursor = LocalDateTime.of(eventDate, startTime);

        // Moderation Start
        Session modStart = buildSession(
                event.getId(), eventCode, "Moderation Start", "moderation",
                cursor, config.getModerationStartDuration());
        addModerator(modStart, organizerUsername);
        createdSessions.add(sessionRepository.save(modStart));
        cursor = cursor.plusMinutes(config.getModerationStartDuration());

        // AM + PM session blocks with breaks and lunch
        int maxSlots = config.getMaxSlots() != null ? config.getMaxSlots() : 0;
        int slotDuration = config.getSlotDuration() != null ? config.getSlotDuration() : 45;
        int breakSlots = config.getBreakSlots() != null ? config.getBreakSlots() : 0;
        int lunchSlots = config.getLunchSlots() != null ? config.getLunchSlots() : 0;

        if (config.getTheoreticalSlotsAM() != null && config.getTheoreticalSlotsAM() && lunchSlots > 0) {
            // AM block: ceil(maxSlots / 2) slots, one break after ceil(amSlots/2) slots
            int amSlots = (int) Math.ceil(maxSlots / 2.0);
            int amBreakAfter = (int) Math.ceil(amSlots / 2.0);
            int amBreaksUsed = 0;
            for (int i = 0; i < amSlots; i++) {
                cursor = cursor.plusMinutes(slotDuration);  // advance past the session gap
                if (i == amBreakAfter - 1 && breakSlots > 0 && amBreaksUsed < breakSlots) {
                    Session breakSession = buildSession(
                            event.getId(), eventCode, "Kaffee-Pause", "break",
                            cursor, config.getBreakDuration());
                    createdSessions.add(sessionRepository.save(breakSession));
                    cursor = cursor.plusMinutes(config.getBreakDuration());
                    amBreaksUsed++;
                }
            }

            // Lunch
            Session lunch = buildSession(
                    event.getId(), eventCode, "Mittagessen", "lunch",
                    cursor, config.getLunchDuration());
            createdSessions.add(sessionRepository.save(lunch));
            cursor = cursor.plusMinutes(config.getLunchDuration());

            // PM block: remaining slots, one break after ceil(pmSlots/2)
            int pmSlots = maxSlots - amSlots;
            int pmBreakAfter = (int) Math.ceil(pmSlots / 2.0);
            int remainingBreakSlots = breakSlots - amBreaksUsed;
            for (int i = 0; i < pmSlots; i++) {
                cursor = cursor.plusMinutes(slotDuration);
                if (i == pmBreakAfter - 1 && remainingBreakSlots > 0) {
                    Session breakSession = buildSession(
                            event.getId(), eventCode, "Pause", "break",
                            cursor, config.getBreakDuration());
                    createdSessions.add(sessionRepository.save(breakSession));
                    cursor = cursor.plusMinutes(config.getBreakDuration());
                    remainingBreakSlots--;
                }
            }
        } else {
            // Simple linear: all slots, one break in the middle
            int breakAfter = breakSlots > 0 ? (int) Math.ceil(maxSlots / 2.0) : -1;
            for (int i = 0; i < maxSlots; i++) {
                cursor = cursor.plusMinutes(slotDuration);
                if (i == breakAfter - 1 && breakSlots > 0) {
                    Session breakSession = buildSession(
                            event.getId(), eventCode, "Pause", "break",
                            cursor, config.getBreakDuration());
                    createdSessions.add(sessionRepository.save(breakSession));
                    cursor = cursor.plusMinutes(config.getBreakDuration());
                }
            }
        }

        // Moderation End
        Session modEnd = buildSession(
                event.getId(), eventCode, "Moderation End", "moderation",
                cursor, config.getModerationEndDuration());
        addModerator(modEnd, organizerUsername);
        createdSessions.add(sessionRepository.save(modEnd));

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
            LocalDateTime start,
            int durationMinutes) {

        String baseSlug = slugGenerationService.generateSessionSlug(title);
        String slug = slugGenerationService.ensureUniqueSlug(
                baseSlug, sessionRepository::existsBySessionSlug);

        // Use Europe/Zurich so stored UTC instants match what the frontend displays in local time
        ZoneId zurich = ZoneId.of("Europe/Zurich");
        Instant startInstant = start.atZone(zurich).toInstant();
        Instant endInstant = start.plusMinutes(durationMinutes).atZone(zurich).toInstant();

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
}
