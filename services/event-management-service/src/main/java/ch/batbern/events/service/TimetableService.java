package ch.batbern.events.service;

import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.Session;
import ch.batbern.events.dto.SessionResponse;
import ch.batbern.events.dto.TimetableResponse;
import ch.batbern.events.dto.TimetableSlot;
import ch.batbern.events.entity.EventTypeConfiguration;
import ch.batbern.events.exception.EventNotFoundException;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.repository.EventTypeRepository;
import ch.batbern.events.repository.SessionRepository;
import ch.batbern.shared.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Single authoritative timeline algorithm for BATbern event scheduling.
 *
 * This service owns the slot-computation logic that was previously duplicated in:
 * - StructuralSessionService (cursor-advancement loop)
 * - SessionTimingService (gap-based auto-assign)
 * - DragDropSlotAssignment (frontend TIME_SLOTS useMemo)
 *
 * <p>{@link #computeTimeline} is a pure function with no DB access — safe to call
 * in unit tests without mocks and from the frontend's live preview (SchedulePreview.tsx
 * implements the same algorithm locally for instant responsiveness).</p>
 *
 * <p>{@link #getTimetable} enriches the computed timeline with live DB data
 * (structural session slugs, assigned speaker sessions, unassigned sessions).</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TimetableService {

    private static final ZoneId ZURICH = ZoneId.of("Europe/Zurich");

    private static final List<String> STRUCTURAL_TYPES = List.of("moderation", "break", "lunch");

    private final EventRepository eventRepository;
    private final EventTypeRepository eventTypeRepository;
    private final SessionRepository sessionRepository;
    private final SessionService sessionService;

    // ─────────────────────────────────────────────────────────────────────────
    // Public API
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Pure timeline computation — no DB access, deterministic.
     *
     * Emits ALL slot types (MODERATION, SPEAKER_SLOT, BREAK, LUNCH) in chronological
     * order.  Speaker slot indices are 1-based and contiguous across the whole day
     * (never reset when switching from AM to PM block).
     *
     * @param config    Event type configuration (null-safe: uses defaults for missing fields)
     * @param eventDate Calendar date of the event (used to anchor wall-clock times)
     * @return Ordered list of {@link TimetableSlot} covering the full event day
     */
    public List<TimetableSlot> computeTimeline(EventTypeConfiguration config, LocalDate eventDate) {
        List<TimetableSlot> slots = new ArrayList<>();

        // Read config with safe defaults
        LocalTime startTime = config.getTypicalStartTime() != null
                ? config.getTypicalStartTime()
                : LocalTime.of(9, 0);
        int maxSlots = config.getMaxSlots() != null ? config.getMaxSlots() : 0;
        int slotDuration = config.getSlotDuration() != null ? config.getSlotDuration() : 45;
        int breakSlots = config.getBreakSlots() != null ? config.getBreakSlots() : 0;
        int lunchSlots = config.getLunchSlots() != null ? config.getLunchSlots() : 0;
        int modStartDur = config.getModerationStartDuration() != null
                ? config.getModerationStartDuration() : 5;
        int modEndDur = config.getModerationEndDuration() != null
                ? config.getModerationEndDuration() : 5;
        int breakDuration = config.getBreakDuration() != null ? config.getBreakDuration() : 20;
        int lunchDuration = config.getLunchDuration() != null ? config.getLunchDuration() : 60;
        boolean theoreticalSlotsAM = config.getTheoreticalSlotsAM() != null
                && config.getTheoreticalSlotsAM();

        // Anchor cursor to event date + typicalStartTime in Europe/Zurich
        ZonedDateTime cursor = eventDate.atTime(startTime).atZone(ZURICH);
        int slotIndex = 1;

        // --- Moderation Start ---
        ZonedDateTime modStartEnd = cursor.plusMinutes(modStartDur);
        slots.add(TimetableSlot.builder()
                .type(TimetableSlot.Type.MODERATION)
                .startTime(cursor.toInstant())
                .endTime(modStartEnd.toInstant())
                .title("Moderation Start")
                .build());
        cursor = modStartEnd;

        // --- Speaker slots (with breaks and optional lunch) ---
        if (theoreticalSlotsAM && lunchSlots > 0) {
            // AM/PM split
            int amSlots = (int) Math.ceil(maxSlots / 2.0);
            int pmSlots = maxSlots - amSlots;
            int amBreakAfter = (int) Math.ceil(amSlots / 2.0);
            int pmBreakAfter = (int) Math.ceil(pmSlots / 2.0);
            int remainingBreaks = breakSlots;

            // AM block
            for (int i = 0; i < amSlots; i++) {
                ZonedDateTime slotEnd = cursor.plusMinutes(slotDuration);
                slots.add(TimetableSlot.builder()
                        .type(TimetableSlot.Type.SPEAKER_SLOT)
                        .startTime(cursor.toInstant())
                        .endTime(slotEnd.toInstant())
                        .slotIndex(slotIndex++)
                        .build());
                cursor = slotEnd;

                if (i == amBreakAfter - 1 && remainingBreaks > 0) {
                    ZonedDateTime breakEnd = cursor.plusMinutes(breakDuration);
                    slots.add(TimetableSlot.builder()
                            .type(TimetableSlot.Type.BREAK)
                            .startTime(cursor.toInstant())
                            .endTime(breakEnd.toInstant())
                            .title("Kaffee-Pause")
                            .build());
                    cursor = breakEnd;
                    remainingBreaks--;
                }
            }

            // Lunch
            ZonedDateTime lunchEnd = cursor.plusMinutes(lunchDuration);
            slots.add(TimetableSlot.builder()
                    .type(TimetableSlot.Type.LUNCH)
                    .startTime(cursor.toInstant())
                    .endTime(lunchEnd.toInstant())
                    .title("Mittagessen")
                    .build());
            cursor = lunchEnd;

            // PM block
            for (int i = 0; i < pmSlots; i++) {
                ZonedDateTime slotEnd = cursor.plusMinutes(slotDuration);
                slots.add(TimetableSlot.builder()
                        .type(TimetableSlot.Type.SPEAKER_SLOT)
                        .startTime(cursor.toInstant())
                        .endTime(slotEnd.toInstant())
                        .slotIndex(slotIndex++)
                        .build());
                cursor = slotEnd;

                if (i == pmBreakAfter - 1 && remainingBreaks > 0) {
                    ZonedDateTime breakEnd = cursor.plusMinutes(breakDuration);
                    slots.add(TimetableSlot.builder()
                            .type(TimetableSlot.Type.BREAK)
                            .startTime(cursor.toInstant())
                            .endTime(breakEnd.toInstant())
                            .title("Pause")
                            .build());
                    cursor = breakEnd;
                    remainingBreaks--;
                }
            }
        } else {
            // Linear: all slots in one block, one break in the middle
            int breakAfter = breakSlots > 0 ? (int) Math.ceil(maxSlots / 2.0) : -1;
            for (int i = 0; i < maxSlots; i++) {
                ZonedDateTime slotEnd = cursor.plusMinutes(slotDuration);
                slots.add(TimetableSlot.builder()
                        .type(TimetableSlot.Type.SPEAKER_SLOT)
                        .startTime(cursor.toInstant())
                        .endTime(slotEnd.toInstant())
                        .slotIndex(slotIndex++)
                        .build());
                cursor = slotEnd;

                if (i == breakAfter - 1 && breakSlots > 0) {
                    ZonedDateTime breakEnd = cursor.plusMinutes(breakDuration);
                    slots.add(TimetableSlot.builder()
                            .type(TimetableSlot.Type.BREAK)
                            .startTime(cursor.toInstant())
                            .endTime(breakEnd.toInstant())
                            .title("Pause")
                            .build());
                    cursor = breakEnd;
                }
            }
        }

        // --- Moderation End ---
        slots.add(TimetableSlot.builder()
                .type(TimetableSlot.Type.MODERATION)
                .startTime(cursor.toInstant())
                .endTime(cursor.plusMinutes(modEndDur).toInstant())
                .title("Moderation End")
                .build());

        return slots;
    }

    /**
     * Compute the timetable for an event and enrich it with live DB data.
     *
     * <ul>
     *   <li>Structural TimetableSlots are matched to DB sessions by exact {@code startTime}
     *       → {@code sessionSlug} is populated when found.</li>
     *   <li>SPEAKER_SLOT entries are matched to assigned speaker sessions by {@code startTime}
     *       → {@code assignedSessionSlug} is populated when a session occupies that slot.</li>
     *   <li>Speaker sessions with {@code null} startTime are returned in
     *       {@code unassignedSessions}.</li>
     * </ul>
     *
     * @param eventCode Public event identifier (e.g., "BATbern142")
     * @return Fully-enriched TimetableResponse
     * @throws EventNotFoundException if the event is not found
     * @throws NotFoundException      if the event has no event type configured
     */
    @Transactional(readOnly = true)
    public TimetableResponse getTimetable(String eventCode) {
        // Load event
        Event event = eventRepository.findByEventCode(eventCode)
                .orElseThrow(() -> new EventNotFoundException(
                        "Event not found with code: " + eventCode));

        // Load config
        if (event.getEventType() == null) {
            throw new NotFoundException("Event '" + eventCode + "' has no event type configured");
        }
        EventTypeConfiguration config = eventTypeRepository.findByType(event.getEventType())
                .orElseThrow(() -> new NotFoundException(
                        "Event type configuration not found for: " + event.getEventType()));

        // Compute virtual timeline
        LocalDate eventDate = event.getDate()
                .atZone(ZURICH)
                .toLocalDate();
        List<TimetableSlot> virtualSlots = computeTimeline(config, eventDate);

        // Load all DB sessions for this event
        List<Session> allSessions = sessionRepository.findByEventId(event.getId());

        // Index: startTime → structural DB session (for slug matching)
        Map<Instant, Session> structuralByStart = allSessions.stream()
                .filter(s -> STRUCTURAL_TYPES.contains(s.getSessionType()))
                .filter(s -> s.getStartTime() != null)
                .collect(Collectors.toMap(Session::getStartTime, s -> s, (a, b) -> a));

        // Index: startTime → assigned speaker session (non-structural, has startTime)
        Map<Instant, Session> speakerByStart = allSessions.stream()
                .filter(s -> !STRUCTURAL_TYPES.contains(s.getSessionType()))
                .filter(s -> s.getStartTime() != null)
                .collect(Collectors.toMap(Session::getStartTime, s -> s, (a, b) -> a));

        // Enrich virtual slots with DB data
        List<TimetableSlot> enrichedSlots = virtualSlots.stream()
                .map(slot -> {
                    if (slot.getType() == TimetableSlot.Type.SPEAKER_SLOT) {
                        Session assigned = speakerByStart.get(slot.getStartTime());
                        if (assigned != null) {
                            return TimetableSlot.builder()
                                    .type(slot.getType())
                                    .startTime(slot.getStartTime())
                                    .endTime(slot.getEndTime())
                                    .title(slot.getTitle())
                                    .slotIndex(slot.getSlotIndex())
                                    .assignedSessionSlug(assigned.getSessionSlug())
                                    .build();
                        }
                    } else {
                        Session structural = structuralByStart.get(slot.getStartTime());
                        if (structural != null) {
                            return TimetableSlot.builder()
                                    .type(slot.getType())
                                    .startTime(slot.getStartTime())
                                    .endTime(slot.getEndTime())
                                    .title(slot.getTitle())
                                    .slotIndex(slot.getSlotIndex())
                                    .sessionSlug(structural.getSessionSlug())
                                    .build();
                        }
                    }
                    return slot;
                })
                .toList();

        // Collect unassigned speaker sessions
        List<SessionResponse> unassigned = allSessions.stream()
                .filter(s -> !STRUCTURAL_TYPES.contains(s.getSessionType()))
                .filter(s -> s.getStartTime() == null)
                .map(s -> sessionService.toSessionResponse(s, eventCode))
                .toList();

        log.debug("getTimetable({}): {} slots, {} unassigned speaker sessions",
                eventCode, enrichedSlots.size(), unassigned.size());

        return TimetableResponse.builder()
                .slots(enrichedSlots)
                .unassignedSessions(unassigned)
                .build();
    }
}
