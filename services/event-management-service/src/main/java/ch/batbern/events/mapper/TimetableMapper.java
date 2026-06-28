package ch.batbern.events.mapper;

import ch.batbern.events.sessions.dto.generated.TimetableResponse;
import ch.batbern.events.sessions.dto.generated.TimetableSlot;
import org.springframework.stereotype.Component;

import java.time.ZoneOffset;
import java.util.List;

/**
 * Boundary mapper: converts the internal scheduling value objects
 * ({@link ch.batbern.events.dto.TimetableResponse} / {@link ch.batbern.events.dto.TimetableSlot})
 * into the generated wire DTOs of the same name.
 *
 * <p>The internal {@code TimetableSlot} is a dual-purpose computation/keying model
 * ({@link java.time.Instant}-based arithmetic + {@code Map<Instant, Session>} slot↔session
 * matching in {@link ch.batbern.events.service.TimetableService}). It is deliberately kept as the
 * internal model; this mapper produces the generated wire shape only at the controller boundary
 * (timestamps {@code Instant → OffsetDateTime} at UTC — same {@code …Z} wire form; {@code Type →
 * TypeEnum} by identical name). {@code unassignedSessions} is already the generated
 * {@code SessionResponse}, so it passes through unchanged.
 */
@Component
public class TimetableMapper {

    /**
     * Map the internal timetable response to the generated wire DTO.
     *
     * @param internal the internal {@link ch.batbern.events.dto.TimetableResponse}
     * @return the generated wire {@link TimetableResponse}, or null if input is null
     */
    public TimetableResponse toWire(ch.batbern.events.dto.TimetableResponse internal) {
        if (internal == null) {
            return null;
        }
        List<TimetableSlot> slots = internal.getSlots() == null
                ? null
                : internal.getSlots().stream().map(this::toWireSlot).toList();
        return TimetableResponse.builder()
                .slots(slots)
                .unassignedSessions(internal.getUnassignedSessions())
                .build();
    }

    /**
     * Map a single internal slot to the generated wire slot.
     *
     * @param slot the internal {@link ch.batbern.events.dto.TimetableSlot}
     * @return the generated wire {@link TimetableSlot}, or null if input is null
     */
    public TimetableSlot toWireSlot(ch.batbern.events.dto.TimetableSlot slot) {
        if (slot == null) {
            return null;
        }
        return TimetableSlot.builder()
                .type(slot.getType() != null
                        ? TimetableSlot.TypeEnum.valueOf(slot.getType().name()) : null)
                .slotKey(slot.getSlotKey())
                .startTime(slot.getStartTime() != null
                        ? slot.getStartTime().atOffset(ZoneOffset.UTC) : null)
                .endTime(slot.getEndTime() != null
                        ? slot.getEndTime().atOffset(ZoneOffset.UTC) : null)
                .title(slot.getTitle())
                .slotIndex(slot.getSlotIndex())
                .sessionSlug(slot.getSessionSlug())
                .assignedSessionSlug(slot.getAssignedSessionSlug())
                .build();
    }
}
