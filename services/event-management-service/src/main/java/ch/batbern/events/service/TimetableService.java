package ch.batbern.events.service;

import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.Session;
import ch.batbern.events.dto.SessionResponse;
import ch.batbern.events.dto.TimetableResponse;
import ch.batbern.events.dto.TimetableSlot;
import ch.batbern.events.entity.AgendaConfig;
import ch.batbern.events.exception.EventNotFoundException;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.repository.SessionRepository;
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
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

    private static final Set<String> STRUCTURAL_TYPES =
            Set.of("moderation", "break", "lunch", "aperitif");

    private static boolean isStructural(String sessionType) {
        return sessionType != null && STRUCTURAL_TYPES.contains(sessionType);
    }

    private final EventRepository eventRepository;
    private final AgendaConfigResolver agendaConfigResolver;
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
    public List<TimetableSlot> computeTimeline(AgendaConfig config, LocalDate eventDate) {
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
        int aperitifSlots = config.getAperitifSlots() != null ? config.getAperitifSlots() : 0;
        int aperitifDuration = config.getAperitifDuration() != null ? config.getAperitifDuration() : 90;
        boolean aperitifAtStart = aperitifSlots > 0
                && "start".equalsIgnoreCase(config.getAperitifPosition());
        boolean aperitifAtEnd = aperitifSlots > 0 && !aperitifAtStart;

        // Anchor cursor to event date + typicalStartTime in Europe/Zurich
        ZonedDateTime cursor = eventDate.atTime(startTime).atZone(ZURICH);
        int slotIndex = 1;

        // --- Moderation Start ---
        cursor = addSlot(slots, TimetableSlot.Type.MODERATION, cursor, modStartDur,
                "Moderation Start", null);

        // --- Apéro at start (after moderation-start) ---
        if (aperitifAtStart) {
            cursor = addSlot(slots, TimetableSlot.Type.APERITIF, cursor, aperitifDuration,
                    "Apéro", null);
        }

        // --- Speaker slots (with breaks and optional lunch) ---
        if (theoreticalSlotsAM && lunchSlots > 0) {
            // AM/PM split — unchanged from the legacy algorithm (preserves full_day parity)
            int amSlots = (int) Math.ceil(maxSlots / 2.0);
            int pmSlots = maxSlots - amSlots;
            int amBreakAfter = (int) Math.ceil(amSlots / 2.0);
            int pmBreakAfter = (int) Math.ceil(pmSlots / 2.0);
            int remainingBreaks = breakSlots;

            for (int i = 0; i < amSlots; i++) {
                cursor = addSlot(slots, TimetableSlot.Type.SPEAKER_SLOT, cursor, slotDuration,
                        null, slotIndex++);
                if (i == amBreakAfter - 1 && remainingBreaks > 0) {
                    cursor = addSlot(slots, TimetableSlot.Type.BREAK, cursor, breakDuration,
                            "Kaffee-Pause", null);
                    remainingBreaks--;
                }
            }

            cursor = addSlot(slots, TimetableSlot.Type.LUNCH, cursor, lunchDuration,
                    "Mittagessen", null);

            for (int i = 0; i < pmSlots; i++) {
                cursor = addSlot(slots, TimetableSlot.Type.SPEAKER_SLOT, cursor, slotDuration,
                        null, slotIndex++);
                if (i == pmBreakAfter - 1 && remainingBreaks > 0) {
                    cursor = addSlot(slots, TimetableSlot.Type.BREAK, cursor, breakDuration,
                            "Pause", null);
                    remainingBreaks--;
                }
            }
        } else {
            // Linear: breaks distributed evenly (count-driven). For breakSlots == 1 this is
            // ceil(maxSlots / 2) — identical to the legacy single break.
            Set<Integer> breakAfterSlots = new HashSet<>();
            for (int k = 1; k <= breakSlots; k++) {
                int pos = (int) Math.ceil((double) (maxSlots * k) / (breakSlots + 1));
                if (pos >= 1 && pos < maxSlots) {
                    breakAfterSlots.add(pos);
                }
            }
            for (int i = 0; i < maxSlots; i++) {
                cursor = addSlot(slots, TimetableSlot.Type.SPEAKER_SLOT, cursor, slotDuration,
                        null, slotIndex++);
                if (breakAfterSlots.contains(i + 1)) {
                    cursor = addSlot(slots, TimetableSlot.Type.BREAK, cursor, breakDuration,
                            "Pause", null);
                }
            }
        }

        // --- Moderation End ---
        cursor = addSlot(slots, TimetableSlot.Type.MODERATION, cursor, modEndDur,
                "Moderation End", null);

        // --- Apéro at end (after moderation-end; the final segment of the day) ---
        if (aperitifAtEnd) {
            addSlot(slots, TimetableSlot.Type.APERITIF, cursor, aperitifDuration, "Apéro", null);
        }

        return stampSlotKeys(slots);
    }

    /**
     * Stamp a deterministic {@code slotKey} on every slot: {@code "{Type}-{ordinal}"} where
     * ordinal is 1-based per type in computed order (Story 15.3). The key is the stable
     * addressing handle used by the UI and the slot-assign endpoint — independent of wall-clock
     * time, so it survives timing/config edits.
     */
    private static List<TimetableSlot> stampSlotKeys(List<TimetableSlot> slots) {
        Map<TimetableSlot.Type, Integer> ordinals = new EnumMap<>(TimetableSlot.Type.class);
        List<TimetableSlot> keyed = new ArrayList<>(slots.size());
        for (TimetableSlot slot : slots) {
            int ordinal = ordinals.merge(slot.getType(), 1, Integer::sum);
            keyed.add(slot.toBuilder()
                    .slotKey(slot.getType().name() + "-" + ordinal)
                    .build());
        }
        return keyed;
    }

    /**
     * Append a slot starting at {@code start} lasting {@code durationMinutes}; returns its end
     * instant (the next cursor). {@code index} is the 1-based speaker-slot index, or null for
     * structural slots.
     */
    private static ZonedDateTime addSlot(List<TimetableSlot> slots, TimetableSlot.Type type,
            ZonedDateTime start, int durationMinutes, String title, Integer index) {
        ZonedDateTime end = start.plusMinutes(durationMinutes);
        slots.add(TimetableSlot.builder()
                .type(type)
                .startTime(start.toInstant())
                .endTime(end.toInstant())
                .title(title)
                .slotIndex(index)
                .build());
        return end;
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

        // Resolve effective config (per-event override if present, else shared template)
        AgendaConfig config = agendaConfigResolver.resolve(event);

        // Compute virtual timeline
        LocalDate eventDate = event.getDate()
                .atZone(ZURICH)
                .toLocalDate();
        List<TimetableSlot> virtualSlots = computeTimeline(config, eventDate);

        // Load all DB sessions for this event
        List<Session> allSessions = sessionRepository.findByEventId(event.getId());

        // Index: startTime → structural DB session (for slug matching)
        Map<Instant, Session> structuralByStart = allSessions.stream()
                .filter(s -> isStructural(s.getSessionType()))
                .filter(s -> s.getStartTime() != null)
                .collect(Collectors.toMap(Session::getStartTime, s -> s, (a, b) -> a));

        // Index: startTime → assigned speaker session (non-structural, has startTime)
        Map<Instant, Session> speakerByStart = allSessions.stream()
                .filter(s -> !isStructural(s.getSessionType()))
                .filter(s -> s.getStartTime() != null)
                .collect(Collectors.toMap(Session::getStartTime, s -> s, (a, b) -> a));

        // Enrich virtual slots with DB data. Binding stays time-based (sessions hold exact
        // computed times); slotKey is carried through unchanged via toBuilder (Story 15.3).
        List<TimetableSlot> enrichedSlots = virtualSlots.stream()
                .map(slot -> {
                    if (slot.getType() == TimetableSlot.Type.SPEAKER_SLOT) {
                        Session assigned = speakerByStart.get(slot.getStartTime());
                        if (assigned != null) {
                            return slot.toBuilder()
                                    .assignedSessionSlug(assigned.getSessionSlug())
                                    .build();
                        }
                    } else {
                        Session structural = structuralByStart.get(slot.getStartTime());
                        if (structural != null) {
                            return slot.toBuilder()
                                    .sessionSlug(structural.getSessionSlug())
                                    .build();
                        }
                    }
                    return slot;
                })
                .toList();

        // Collect unassigned speaker sessions
        List<SessionResponse> unassigned = allSessions.stream()
                .filter(s -> !isStructural(s.getSessionType()))
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
