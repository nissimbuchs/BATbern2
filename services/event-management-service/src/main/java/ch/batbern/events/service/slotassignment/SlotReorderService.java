package ch.batbern.events.service.slotassignment;

import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.Session;
import ch.batbern.events.dto.TimetableResponse;
import ch.batbern.events.dto.TimetableSlot;
import ch.batbern.events.exception.AgendaFullException;
import ch.batbern.events.exception.EventNotFoundException;
import ch.batbern.events.exception.InvalidSlotAssignmentException;
import ch.batbern.events.exception.SessionNotFoundException;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.repository.SessionRepository;
import ch.batbern.events.service.TimetableService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * Stable slot assignment (Story 15.3): place a speaker session onto a slot addressed by its
 * deterministic {@link TimetableSlot#getSlotKey() slotKey} rather than by wall-clock time, and
 * support ASSIGN / INSERT (shift + reflow) / SWAP — all in a single transaction.
 *
 * <p>This service does NOT change how sessions persist their times. It computes the affected
 * sessions' new slot positions, then writes each one's {@code startTime}/{@code endTime} through
 * the existing {@link SessionTimingService#assignTiming} contract (which also records history and
 * publishes the workflow event). The computed timeline is the single source of slot times.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class SlotReorderService {

    private static final String DEFAULT_ROOM = "Main Hall";

    private final EventRepository eventRepository;
    private final SessionRepository sessionRepository;
    private final TimetableService timetableService;
    private final SessionTimingService sessionTimingService;

    /**
     * Apply a slot assignment and return the recomputed timetable.
     *
     * @param eventCode     public event identifier
     * @param sessionSlug   the dragged speaker session
     * @param targetSlotKey slotKey of the target SPEAKER_SLOT (e.g. "SPEAKER_SLOT-3")
     * @param mode          ASSIGN / INSERT / SWAP
     * @param changedBy     username for history attribution
     * @return the timetable after the change
     */
    public TimetableResponse assignToSlot(String eventCode, String sessionSlug,
            String targetSlotKey, SlotAssignmentMode mode, String changedBy) {

        log.info("Slot assignment {} on event {}: session {} -> {}",
                mode, eventCode, sessionSlug, targetSlotKey);

        Event event = eventRepository.findByEventCode(eventCode)
                .orElseThrow(() -> new EventNotFoundException(eventCode));

        Session dragged = sessionRepository.findBySessionSlug(sessionSlug)
                .orElseThrow(() -> new SessionNotFoundException(sessionSlug));
        if (!event.getId().equals(dragged.getEventId())) {
            throw new SessionNotFoundException(sessionSlug);
        }

        // Ordered snapshot of computed speaker slots + their current occupants.
        List<TimetableSlot> speakerSlots = timetableService.getTimetable(eventCode).getSlots()
                .stream()
                .filter(s -> s.getType() == TimetableSlot.Type.SPEAKER_SLOT)
                .toList();

        int targetIdx = indexOfSlotKey(speakerSlots, targetSlotKey);
        if (targetIdx < 0) {
            throw new InvalidSlotAssignmentException(
                    "Unknown target slot '" + targetSlotKey + "' for event " + eventCode);
        }

        List<String> occupants = new ArrayList<>();
        for (TimetableSlot s : speakerSlots) {
            occupants.add(s.getAssignedSessionSlug());
        }
        // Null-tolerant snapshot (free slots are null — List.copyOf rejects nulls).
        List<String> original = new ArrayList<>(occupants);

        switch (mode) {
            case ASSIGN -> applyAssign(occupants, targetIdx, sessionSlug);
            case SWAP -> applySwap(occupants, targetIdx, sessionSlug);
            case INSERT -> applyInsert(occupants, targetIdx, sessionSlug, eventCode);
            default -> throw new InvalidSlotAssignmentException("Unsupported mode: " + mode);
        }

        persistMoves(speakerSlots, original, occupants, changedBy);

        return timetableService.getTimetable(eventCode);
    }

    // ── Mode handlers (operate on the in-memory occupant array) ─────────────────────

    private void applyAssign(List<String> occupants, int targetIdx, String sessionSlug) {
        String occupant = occupants.get(targetIdx);
        if (occupant != null && !occupant.equals(sessionSlug)) {
            throw new InvalidSlotAssignmentException(
                    "ASSIGN target slot is occupied; use INSERT or SWAP");
        }
        // Remove the dragged session from any prior slot, then place it on the empty target.
        clearSlug(occupants, sessionSlug);
        occupants.set(targetIdx, sessionSlug);
    }

    private void applySwap(List<String> occupants, int targetIdx, String sessionSlug) {
        String occupant = occupants.get(targetIdx);
        if (occupant == null) {
            throw new InvalidSlotAssignmentException("SWAP target slot is empty; use ASSIGN");
        }
        if (occupant.equals(sessionSlug)) {
            return; // no-op: dropped onto itself
        }
        int draggedIdx = occupants.indexOf(sessionSlug);
        if (draggedIdx < 0) {
            throw new InvalidSlotAssignmentException(
                    "SWAP requires the dragged session to already occupy a slot");
        }
        occupants.set(draggedIdx, occupant);
        occupants.set(targetIdx, sessionSlug);
    }

    private void applyInsert(List<String> occupants, int targetIdx, String sessionSlug,
            String eventCode) {
        // Detach the dragged session so its current slot (if any) can absorb a shift.
        clearSlug(occupants, sessionSlug);

        // Find the first free slot at or after the target to absorb the cascade.
        int freeIdx = -1;
        for (int i = targetIdx; i < occupants.size(); i++) {
            if (occupants.get(i) == null) {
                freeIdx = i;
                break;
            }
        }
        if (freeIdx < 0) {
            throw new AgendaFullException(eventCode);
        }
        // Shift [targetIdx, freeIdx) one slot later (from the free end backwards).
        for (int i = freeIdx; i > targetIdx; i--) {
            occupants.set(i, occupants.get(i - 1));
        }
        occupants.set(targetIdx, sessionSlug);
    }

    // ── Persistence ─────────────────────────────────────────────────────────────────

    /**
     * Persist every session whose slot index changed, retiming it to its new slot's computed
     * start/end. Each session keeps its existing room (default Main Hall). Single transaction.
     */
    private void persistMoves(List<TimetableSlot> speakerSlots, List<String> original,
            List<String> updated, String changedBy) {
        // Reuse the existing allowed change_reason (V28 CHECK constraint) — no migration needed.
        String reason = "drag_drop_reassignment";
        for (int i = 0; i < updated.size(); i++) {
            String slug = updated.get(i);
            if (slug == null) {
                continue;
            }
            // Only retime sessions that actually moved to a different index.
            int previousIdx = original.indexOf(slug);
            if (previousIdx == i) {
                continue;
            }
            TimetableSlot slot = speakerSlots.get(i);
            String room = resolveRoom(slug);
            sessionTimingService.assignTiming(slug, slot.getStartTime(), slot.getEndTime(),
                    room, reason, changedBy);
        }
    }

    private String resolveRoom(String sessionSlug) {
        return sessionRepository.findBySessionSlug(sessionSlug)
                .map(Session::getRoom)
                .filter(r -> r != null && !r.isBlank())
                .orElse(DEFAULT_ROOM);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────────

    private static int indexOfSlotKey(List<TimetableSlot> slots, String slotKey) {
        for (int i = 0; i < slots.size(); i++) {
            if (slotKey != null && slotKey.equals(slots.get(i).getSlotKey())) {
                return i;
            }
        }
        return -1;
    }

    private static void clearSlug(List<String> occupants, String sessionSlug) {
        int idx = occupants.indexOf(sessionSlug);
        if (idx >= 0) {
            occupants.set(idx, null);
        }
    }
}
